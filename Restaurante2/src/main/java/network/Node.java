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
            boolean souOPrimeiro = (liderAtual == null || liderAtual.startsWith("ERRO"));

            if (!souOPrimeiro) {
                System.out.println(">>> CLUSTER DETECTADO! Líder atual em: " + liderAtual);

                String[] partes = liderAtual.split(":");
                String ipLider = partes[0];
                int portaWebLider = Integer.parseInt(partes[1]);
                int portaInternaLider = portaWebLider - 6000; // Ex: 9002 -> 3002

                if (portaInternaLider != this.portaInterna) {
                    System.out.println(">>> Configurando Ratis para iniciar com o líder conhecido...");

                    RaftPeer peerLider = RaftPeer.newBuilder()
                            .setId("n_" + portaInternaLider)
                            .setAddress(ipLider + ":" + portaInternaLider)
                            .build();

                    boolean jaTem = this.peersDoCluster.stream().anyMatch(p -> p.getId().equals(peerLider.getId()));
                    if (!jaTem) {
                        this.peersDoCluster.add(peerLider);
                    }
                } else {
                    System.out.println(">>> ALERTA: O DNS aponta para MIM como líder, mas estou reiniciando.");
                    System.out.println(">>> Vou assumir que sou o líder (Recuperação de falha).");
                    souOPrimeiro = true; // Força virar líder
                }
            }
            if (souOPrimeiro) {
                System.out.println(">>> NENHUM LÍDER VÁLIDO DETECTADO. Vou iniciar como LÍDER (Seed).");
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
            }

            // CORREÇÃO CRÍTICA: Monitoramento inicia SEMPRE, para todos os nós
            new Thread(this::monitorarLideranca).start();

            new Thread(this::inicializarEstoqueSimulado).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void solicitarEntradaNoCluster() {
        new Thread(() -> {
            try {
                System.out.println(">>> [AUTO-JOIN] Procurando líder no DNS para pedir entrada...");

                String enderecoLiderWeb = null;
                while (enderecoLiderWeb == null || enderecoLiderWeb.startsWith("ERRO")) {
                    enderecoLiderWeb = dnsClient.descobrirLider();
                    if (enderecoLiderWeb == null) Thread.sleep(2000);
                }

                System.out.println(">>> [AUTO-JOIN] Líder encontrado (WEB): " + enderecoLiderWeb);

                String[] partes = enderecoLiderWeb.split(":");
                String ipLider = partes[0];
                int portaWebLider = Integer.parseInt(partes[1]);
                int portaRatisLider = 3000 + (portaWebLider % 100);

                String enderecoRatisLider = ipLider + ":" + portaRatisLider;
                System.out.println(">>> [AUTO-JOIN] Tentando conectar no Ratis Líder: " + enderecoRatisLider);

                RaftPeer peerLider = RaftPeer.newBuilder().setId("lider_temp").setAddress(enderecoRatisLider).build();
                RaftGroup grupoLider = RaftGroup.valueOf(RaftGroupId.valueOf(UUID.fromString("02511d47-d67c-49a3-9011-abb3109a44c1")), peerLider);

                try (RaftClient clientLider = RaftClient.newBuilder()
                        .setProperties(new RaftProperties())
                        .setRaftGroup(grupoLider)
                        .build()) {

                    System.out.println(">>> [AUTO-JOIN] Enviando comando 'REQ_ADD_MEMBER'...");
                    String cmd = "REQ_ADD_MEMBER:" + peerIdStr + ":" + meuIp + ":" + portaInterna;

                    RaftClientReply reply = clientLider.io().send(Message.valueOf(cmd));
                    String resp = reply.getMessage().getContent().toString(StandardCharsets.UTF_8);

                    System.out.println(">>> [AUTO-JOIN] Resposta do Líder: " + resp);

                    // A thread de monitoramento já foi iniciada no método iniciar(),
                    // então não precisamos iniciá-la aqui novamente.
                }

            } catch (Exception e) {
                System.err.println("Erro no Auto-Join: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    public void adicionarNovoMembro(String novoId, String novoIp, int novaPorta) {
        if (!souLider) return;

        try {
            System.out.println(">>> [ADMIN] Iniciando processo de adição do nó " + novoId);

            RaftConfiguration confAtual = raftServer.getDivision(raftServer.getGroupIds().iterator().next()).getRaftConf();
            List<RaftPeer> peersAtuais = new ArrayList<>(confAtual.getAllPeers());

            boolean jaExiste = peersAtuais.stream().anyMatch(p -> p.getId().toString().equals(novoId));
            if (jaExiste) {
                System.out.println(">>> [ADMIN] Nó " + novoId + " já existe no cluster.");
                return;
            }

            RaftPeer novoPeer = RaftPeer.newBuilder()
                    .setId(novoId)
                    .setAddress(novoIp + ":" + novaPorta)
                    .build();
            peersAtuais.add(novoPeer);

            System.out.println(">>> [ADMIN] Enviando nova configuração para o cluster...");
            RaftClientReply reply = raftClient.admin().setConfiguration(peersAtuais);

            if (reply.isSuccess()) {
                System.out.println(">>> [ADMIN] Sucesso! Cluster expandido para " + peersAtuais.size() + " nós.");
            } else {
                System.err.println(">>> [ADMIN] Falha ao adicionar nó: " + reply.getException());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void inicializarEstoqueSimulado() {
        try {
            Thread.sleep(8000);
            if (this.estoqueInicial == null || this.estoqueInicial.isEmpty()) return;

            for (Map.Entry<String, Integer> item : this.estoqueInicial.entrySet()) {
                String cmd = "INIT_ESTOQUE:" + peerIdStr + ":" + item.getKey() + ":" + item.getValue();

                raftClient.io().send(Message.valueOf(cmd));
                System.out.println(">>> [ESTOQUE] Solicitando inicialização: " + item.getKey());
            }
        } catch (Exception e) {
            System.err.println("Erro estoque: " + e.getMessage());
        }
    }

    private RaftGroup montarGrupoRatis() {
        RaftGroupId groupId = RaftGroupId.valueOf(UUID.fromString("02511d47-d67c-49a3-9011-abb3109a44c1"));
        return RaftGroup.valueOf(groupId, this.peersDoCluster);
    }

    // --- MÉTODO MONITORAR LIDERANÇA CORRIGIDO ---
    private void monitorarLideranca() {
        RaftProtos.RaftPeerRole roleAnterior = null;
        long termoAnterior = -1;

        while (true) {
            try {
                Thread.sleep(1000); // 1 segundo

                if (raftServer == null || raftServer.getLifeCycleState() != LifeCycle.State.RUNNING) {
                    continue;
                }

                // CORREÇÃO: Usando iterator() pois Iterable não tem isEmpty()
                Iterable<RaftGroupId> groupIds = raftServer.getGroupIds();
                if (!groupIds.iterator().hasNext()) continue;

                RaftGroupId groupId = groupIds.iterator().next();
                RaftServer.Division divisao = raftServer.getDivision(groupId);

                if (divisao == null || divisao.getInfo() == null) continue;

                RaftProtos.RaftPeerRole roleAtual = divisao.getInfo().getCurrentRole();
                long termoAtual = divisao.getInfo().getCurrentTerm();
                boolean souLiderAgora = divisao.getInfo().isLeader();

                if (roleAtual != roleAnterior) {
                    System.out.println(">>> [RAFT CHANGE] Cargo mudou: " +
                            (roleAnterior == null ? "INICIO" : roleAnterior) +
                            " ---> " + roleAtual +
                            " (Termo: " + termoAtual + ")");

                    roleAnterior = roleAtual;
                }

                if (termoAtual != termoAnterior) {
                    System.out.println(">>> [RAFT ELECTION] Novo Termo Eleitoral iniciado: " + termoAtual);
                    termoAnterior = termoAtual;
                }

                if (souLiderAgora && !this.souLider) {
                    assumirLideranca();
                } else if (!souLiderAgora && this.souLider) {
                    perderLideranca();
                }

            } catch (Exception e) {
                // Log para garantir que a thread não morreu silenciosamente
                System.err.println(">>> [MONITOR ERROR] Erro na thread de monitoramento (tentando novamente): " + e.getMessage());
                e.printStackTrace();
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
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
            System.err.println("ERRO CRÍTICO AO SUBIR WS: " + e.getMessage());
            this.souLider = false;
        }
    }

    private void perderLideranca() {
        this.souLider = false;
        System.out.println(">>> [EVENTO] PERDI A LIDERANÇA.");
        if (endpointAtual != null) endpointAtual.stop();
    }

    public int processarCadastro(String restaurante) {
        if (!souLider) return -1;
        try {
            String comando = "CADASTRO:" + restaurante;
            RaftClientReply reply = raftClient.io().send(Message.valueOf(comando));
            return Integer.parseInt(reply.getMessage().getContent().toString(StandardCharsets.UTF_8));
        } catch (Exception e) { return -1; }
    }

    public boolean processarCompra(int idRestaurante, String[] produtos) {
        if (!souLider) return false;
        try {
            if (!stateMachine.existeRestaurante(idRestaurante)) return false;
            String arrayStr = String.join(",", produtos);
            String comando = "COMPRA:" + idRestaurante + ":" + arrayStr;
            RaftClientReply reply = raftClient.io().send(Message.valueOf(comando));
            String resp = reply.getMessage().getContent().toString(StandardCharsets.UTF_8);
            return resp.equals("OK");
        } catch (Exception e) { return false; }
    }

    public int calcularTempoEntrega(int idRestaurante) {
        if (!stateMachine.existeRestaurante(idRestaurante)) return -1;
        return (new Random().nextInt(10) + 1) * 1000;
    }
}