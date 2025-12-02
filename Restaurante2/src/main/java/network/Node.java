package network;

import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.server.RaftConfiguration;
import wsMercado.MercadoServidorImpl;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.*;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.util.LifeCycle;

import javax.xml.ws.Endpoint;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Node {

    private int portaInterna;
    private int portaWebService;
    private String meuIp;
    private DnsClient dnsClient;
    private boolean souLider = false;
    private Endpoint endpointAtual;

    private RaftServer raftServer;
    private RaftClient raftClient;
    private MyStateMachine stateMachine;
    private String peerIdStr;

    private Map<String, Integer> estoqueInicial;
    private List<RaftPeer> peersDoCluster;

    public Node(String dnsIp, int dnsPort, int portaInterna,
                Map<String, Integer> estoqueInicial,
                List<String> enderecosVizinhos) {

        this.portaInterna = portaInterna;
        this.portaWebService = 9000 + (portaInterna % 100);
        this.dnsClient = new DnsClient(dnsIp, dnsPort);
        this.meuIp = NetworkUtils.getIpAddress();
        this.estoqueInicial = estoqueInicial;

        this.peersDoCluster = new ArrayList<>();
        this.peerIdStr = "n_" + portaInterna;

        // Adiciona a mim mesmo
        this.peersDoCluster.add(RaftPeer.newBuilder()
                .setId(peerIdStr)
                .setAddress(meuIp + ":" + portaInterna)
                .build());

        for (String endereco : enderecosVizinhos) {
            if (endereco.trim().isEmpty()) continue;
            String[] partes = endereco.split(":");
            String ipVizinho = partes[0];
            int portaVizinho = Integer.parseInt(partes[1]);

            if (ipVizinho.equals(meuIp) && portaVizinho == portaInterna) continue;

            this.peersDoCluster.add(RaftPeer.newBuilder()
                    .setId("n_" + portaVizinho)
                    .setAddress(endereco)
                    .build());
        }
    }

    public void iniciar() {
        try {
            String liderAtual = dnsClient.descobrirLider();
            boolean clusterExiste = (liderAtual != null && !liderAtual.startsWith("ERRO"));

            if (clusterExiste) {
                System.out.println(">>> DNS diz que o líder é: " + liderAtual);
                if (!isLiderVivo(liderAtual)) {
                    System.out.println(">>> ALERTA: O Líder indicado pelo DNS não responde (Timeout).");
                    System.out.println(">>> Ignorando DNS e iniciando como Seed/Líder.");
                    clusterExiste = false;
                }
            }

            boolean souOPrimeiro = !clusterExiste;

            if (!souOPrimeiro) {
                System.out.println(">>> Cluster validado. Iniciando como FOLLOWER.");

                String[] partes = liderAtual.split(":");
                String ipLider = partes[0];
                int portaWebLider = Integer.parseInt(partes[1]);
                int portaInternaLider = portaWebLider - 6000;

                if (portaInternaLider != this.portaInterna) {
                    RaftPeer peerLider = RaftPeer.newBuilder()
                            .setId("n_" + portaInternaLider)
                            .setAddress(ipLider + ":" + portaInternaLider)
                            .build();

                    boolean jaTem = this.peersDoCluster.stream().anyMatch(p -> p.getId().equals(peerLider.getId()));
                    if (!jaTem) this.peersDoCluster.add(peerLider);
                }
            } else {
                System.out.println(">>> Iniciando como LÍDER (Seed).");
            }

            RaftGroup raftGroup = montarGrupoRatis();
            RaftPeerId meuId = RaftPeerId.valueOf(peerIdStr);

            RaftProperties properties = new RaftProperties();
            File storageDir = new File("./ratis_storage/" + meuId);
            RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(storageDir));
            GrpcConfigKeys.Server.setPort(properties, portaInterna);

            this.stateMachine = new MyStateMachine();
            this.stateMachine.setNode(this);

            this.raftServer = RaftServer.newBuilder()
                    .setServerId(meuId)
                    .setStateMachine(stateMachine)
                    .setGroup(raftGroup)
                    .setProperties(properties)
                    .build();

            this.raftServer.start();
            System.out.println("Ratis Server iniciado em " + meuIp + ":" + portaInterna);

            this.raftClient = RaftClient.newBuilder()
                    .setProperties(properties)
                    .setRaftGroup(raftGroup)
                    .build();

            if (!souOPrimeiro) {
                solicitarEntradaNoCluster();
            } else {
                new Thread(this::monitorarLideranca).start();
            }

            new Thread(this::inicializarEstoqueSimulado).start();

        } catch (Exception e) {
            System.err.println("ERRO FATAL AO INICIAR NÓ:");
            e.printStackTrace();
        }
    }

    private boolean isLiderVivo(String enderecoWeb) {
        try {
            String[] partes = enderecoWeb.split(":");
            String ip = partes[0];
            int porta = Integer.parseInt(partes[1]);
            if (porta == this.portaWebService) return false; // Se sou eu reiniciando, considero morto
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, porta), 500);
                return true;
            }
        } catch (Exception e) { return false; }
    }

    private void inicializarEstoqueSimulado() {
        try {
            Thread.sleep(8000);
            if (this.estoqueInicial == null || this.estoqueInicial.isEmpty()) return;

            for (Map.Entry<String, Integer> item : this.estoqueInicial.entrySet()) {
                String cmd = "INIT_ESTOQUE:" + peerIdStr + ":" + item.getKey() + ":" + item.getValue();
                raftClient.io().send(Message.valueOf(cmd));
                System.out.println(">>> [ESTOQUE] Enviado ao cluster: " + item.getKey());
            }
        } catch (Exception e) {
            System.err.println("Erro ao registrar estoque: " + e.getMessage());
        }
    }

    private RaftGroup montarGrupoRatis() {
        RaftGroupId groupId = RaftGroupId.valueOf(UUID.fromString("02511d47-d67c-49a3-9011-abb3109a44c1"));
        return RaftGroup.valueOf(groupId, this.peersDoCluster);
    }

    private void monitorarLideranca() {
        RaftProtos.RaftPeerRole roleAnterior = null;
        long termoAnterior = -1;

        while (true) {
            try {
                Thread.sleep(500);

                if (raftServer.getLifeCycleState() != LifeCycle.State.RUNNING) continue;

                RaftGroupId groupId = raftServer.getGroupIds().iterator().next();
                RaftServer.Division divisao = raftServer.getDivision(groupId);

                if (divisao.getInfo() == null) continue;

                RaftProtos.RaftPeerRole roleAtual = divisao.getInfo().getCurrentRole();
                long termoAtual = divisao.getInfo().getCurrentTerm();
                boolean souLiderAgora = divisao.getInfo().isLeader();

                // Log de mudança de Estado
                if (roleAtual != roleAnterior) {
                    System.out.println(">>> [RAFT CHANGE] Cargo mudou: " +
                            (roleAnterior == null ? "INICIO" : roleAnterior) + " ---> " + roleAtual);
                    roleAnterior = roleAtual;
                }

                // Log de Eleição
                if (termoAtual != termoAnterior) {
                    System.out.println(">>> [RAFT ELECTION] Novo Termo Eleitoral: " + termoAtual);
                    termoAnterior = termoAtual;
                }

                if (souLiderAgora && !this.souLider) {
                    assumirLideranca();
                } else if (!souLiderAgora && this.souLider) {
                    perderLideranca();
                }

            } catch (Exception e) {
                // Ignora erros pontuais
            }
        }
    }

    private void assumirLideranca() {
        this.souLider = true;
        System.out.println(">>> [EVENTO] VIREI LÍDER! Iniciando WebService...");
        try {
            if (endpointAtual != null && endpointAtual.isPublished()) endpointAtual.stop();

            MercadoServidorImpl implementacaoWs = new MercadoServidorImpl(this);
            String url = "http://" + this.meuIp + ":" + this.portaWebService + "/mercado";

            this.endpointAtual = Endpoint.publish(url, implementacaoWs);
            System.out.println("Web Service NO AR em: " + url);
            dnsClient.registrarSe("LIDER", this.portaWebService);

        } catch (Exception e) {
            System.err.println("ERRO FATAL AO SUBIR WS (LIDERANÇA ABORTADA): " + e.getMessage());
            e.printStackTrace();

            System.out.println(">>> Encerrando processo para permitir que outro nó assuma...");
            System.exit(1);
        }
    }

    private void perderLideranca() {
        this.souLider = false;
        System.out.println(">>> [EVENTO] PERDI A LIDERANÇA.");
        if (endpointAtual != null) endpointAtual.stop();
    }

    // --- CORREÇÃO: LANÇAR EXCEÇÃO SE NÃO FOR LÍDER ---
    public int processarCadastro(String restaurante) {
        if (!souLider) throw new RuntimeException("Não sou o líder. Tente outro nó.");
        try {
            String cmd = "CADASTRO:" + restaurante;
            RaftClientReply reply = raftClient.io().send(Message.valueOf(cmd));
            return Integer.parseInt(reply.getMessage().getContent().toString(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public boolean processarCompra(int idRestaurante, String[] produtos) {
        if (!souLider) throw new RuntimeException("Não sou o líder. Tente outro nó.");
        try {
            if (!stateMachine.existeRestaurante(idRestaurante)) return false;
            String cmd = "COMPRA:" + idRestaurante + ":" + String.join(",", produtos);
            RaftClientReply reply = raftClient.io().send(Message.valueOf(cmd));
            return reply.getMessage().getContent().toString(StandardCharsets.UTF_8).equals("OK");
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public int calcularTempoEntrega(int idRestaurante) {
        if (!stateMachine.existeRestaurante(idRestaurante)) return -1;
        return (new Random().nextInt(10) + 1) * 1000;
    }

    public void solicitarEntradaNoCluster() {
        new Thread(() -> {
            try {
                System.out.println(">>> [AUTO-JOIN] Procurando líder no DNS...");
                String enderecoLiderWeb = null;
                while (enderecoLiderWeb == null || enderecoLiderWeb.startsWith("ERRO")) {
                    enderecoLiderWeb = dnsClient.descobrirLider();
                    if (enderecoLiderWeb == null) Thread.sleep(2000);
                }

                String[] partes = enderecoLiderWeb.split(":");
                String ipLider = partes[0];
                int portaRatisLider = Integer.parseInt(partes[1]) - 6000;
                String enderecoRatisLider = ipLider + ":" + portaRatisLider;

                System.out.println(">>> [AUTO-JOIN] Pedindo entrada para: " + enderecoRatisLider);

                RaftPeer peerLider = RaftPeer.newBuilder().setId("lider_temp").setAddress(enderecoRatisLider).build();
                RaftGroup grupoLider = RaftGroup.valueOf(RaftGroupId.valueOf(UUID.fromString("02511d47-d67c-49a3-9011-abb3109a44c1")), peerLider);

                try (RaftClient clientLider = RaftClient.newBuilder().setProperties(new RaftProperties()).setRaftGroup(grupoLider).build()) {
                    String cmd = "REQ_ADD_MEMBER:" + peerIdStr + ":" + meuIp + ":" + portaInterna;
                    RaftClientReply reply = clientLider.io().send(Message.valueOf(cmd));
                    System.out.println(">>> [AUTO-JOIN] Resposta: " + reply.getMessage().getContent().toString(StandardCharsets.UTF_8));

                    new Thread(this::monitorarLideranca).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void adicionarNovoMembro(String novoId, String novoIp, int novaPorta) {
        if (!souLider) return;
        try {
            System.out.println(">>> [ADMIN] Adicionando nó " + novoId);
            RaftConfiguration confAtual = raftServer.getDivision(raftServer.getGroupIds().iterator().next()).getRaftConf();
            List<RaftPeer> peersAtuais = new ArrayList<>(confAtual.getAllPeers());

            if (peersAtuais.stream().anyMatch(p -> p.getId().toString().equals(novoId))) return;

            RaftPeer novoPeer = RaftPeer.newBuilder().setId(novoId).setAddress(novoIp + ":" + novaPorta).build();
            peersAtuais.add(novoPeer);

            RaftClientReply reply = raftClient.admin().setConfiguration(peersAtuais);
            if (reply.isSuccess()) System.out.println(">>> [ADMIN] Sucesso! Cluster: " + peersAtuais.size() + " nós.");

        } catch (IOException e) { e.printStackTrace(); }
    }
}