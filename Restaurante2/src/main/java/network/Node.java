package network;

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
        this.portaWebService = 8000 + (portaInterna % 100);
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

            String idVizinho = "n_" + portaVizinho;

            this.peersDoCluster.add(RaftPeer.newBuilder()
                    .setId(idVizinho)
                    .setAddress(endereco)
                    .build());
        }
    }

    public void iniciar() {
        try {
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
            System.out.println("Peers conhecidos: " + peersDoCluster.size());

            this.raftClient = RaftClient.newBuilder()
                    .setProperties(properties)
                    .setRaftGroup(raftGroup)
                    .build();

            new Thread(this::inicializarEstoqueSimulado).start();
            new Thread(this::monitorarLideranca).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void inicializarEstoqueSimulado() {
        try {
            Thread.sleep(8000);

            if (this.estoqueInicial == null || this.estoqueInicial.isEmpty()) {
                System.out.println(">>> [ESTOQUE] Sem estoque inicial configurado.");
                return;
            }

            for (Map.Entry<String, Integer> item : this.estoqueInicial.entrySet()) {
                String cmd = "ADD_ESTOQUE:" + peerIdStr + ":" + item.getKey() + ":" + item.getValue();
                raftClient.io().send(Message.valueOf(cmd));
                System.out.println(">>> [ESTOQUE] Enviado ao cluster: " + item.getValue() + "x " + item.getKey());
            }

        } catch (Exception e) {
            System.err.println("Erro ao registrar estoque (o cluster pode estar sem quorum): " + e.getMessage());
        }
    }

    private RaftGroup montarGrupoRatis() {
        RaftGroupId groupId = RaftGroupId.valueOf(UUID.fromString("02511d47-d67c-49a3-9011-abb3109a44c1"));
        return RaftGroup.valueOf(groupId, this.peersDoCluster);
    }

    private void monitorarLideranca() {
        while (true) {
            try {
                Thread.sleep(2000);
                if (raftServer.getLifeCycleState() != LifeCycle.State.RUNNING) continue;

                RaftGroupId groupId = raftServer.getGroupIds().iterator().next();
                boolean souLiderAgora = raftServer.getDivision(groupId).getInfo().isLeader();

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
            if (endpointAtual != null) {
                if (endpointAtual.isPublished()) {
                    endpointAtual.stop();
                }
                endpointAtual = null;
            }

            MercadoServidorImpl implementacaoWs = new MercadoServidorImpl(this);
            String url = "http://" + this.meuIp + ":" + this.portaWebService + "/mercado";

            this.endpointAtual = Endpoint.publish(url, implementacaoWs);

            System.out.println("Web Service NO AR em: " + url);
            dnsClient.registrarSe("LIDER", this.portaWebService);

        } catch (Exception e) {
            System.err.println("ERRO CRÍTICO AO SUBIR WEB SERVICE: " + e.getMessage());
            e.printStackTrace();
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
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int calcularTempoEntrega(int idRestaurante) {
        if (!stateMachine.existeRestaurante(idRestaurante)) return -1;
        return (new Random().nextInt(10) + 1) * 1000;
    }

    public void solicitarEntradaNoCluster() {
        new Thread(() -> {
            try {
                System.out.println(">>> [AUTO-JOIN] Procurando líder no DNS para pedir entrada...");

                // 1. Descobre quem é o líder atual
                String enderecoLider = null;
                while (enderecoLider == null || enderecoLider.startsWith("ERRO")) {
                    enderecoLider = dnsClient.descobrirLider();
                    if (enderecoLider == null) Thread.sleep(1000);
                }

                System.out.println(">>> [AUTO-JOIN] Líder encontrado: " + enderecoLider);

                String[] partes = enderecoLider.split(":"); // IP:PortaWeb

                RaftPeer peerLider = RaftPeer.newBuilder().setId("lider_temp").setAddress(enderecoLider).build();
                RaftGroup grupoLider = RaftGroup.valueOf(RaftGroupId.valueOf(UUID.fromString("02511d47-d67c-49a3-9011-abb3109a44c1")), peerLider);

                try (RaftClient clientLider = RaftClient.newBuilder()
                        .setProperties(new RaftProperties())
                        .setRaftGroup(grupoLider)
                        .build()) {

                    RaftPeer eu = RaftPeer.newBuilder().setId(peerIdStr).setAddress(meuIp + ":" + portaInterna).build();

                    System.out.println(">>> [AUTO-JOIN] Enviando comando setConfiguration para o líder...");

                    String cmd = "REQ_ADD_MEMBER:" + peerIdStr + ":" + meuIp + ":" + portaInterna;
                    RaftClientReply reply = clientLider.io().send(Message.valueOf(cmd));

                    String resp = reply.getMessage().getContent().toString(StandardCharsets.UTF_8);
                    System.out.println(">>> [AUTO-JOIN] Resposta do Líder: " + resp);
                }

            } catch (Exception e) {
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
                System.out.println(">>> [ADMIN] Nó já existe no cluster.");
                return;
            }

            RaftPeer novoPeer = RaftPeer.newBuilder()
                    .setId(novoId)
                    .setAddress(novoIp + ":" + novaPorta)
                    .build();

            peersAtuais.add(novoPeer); // Adiciona na lista

            RaftClientReply reply = raftClient.admin().setConfiguration(peersAtuais);

            if (reply.isSuccess()) {
                System.out.println(">>> [ADMIN] Sucesso! Novo nó " + novoId + " adicionado ao cluster.");
            } else {
                System.err.println(">>> [ADMIN] Falha ao adicionar nó: " + reply.getException());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}