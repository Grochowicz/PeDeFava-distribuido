package network;

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

    private static final List<Integer> PORTAS_DO_CLUSTER = Arrays.asList(3000, 3001, 3002);

    public Node(String dnsIp, int dnsPort, int portaInterna, Map<String, Integer> estoqueInicial) {
        this.portaInterna = portaInterna;
        this.portaWebService = 8000 + (portaInterna % 100);
        this.dnsClient = new DnsClient(dnsIp, dnsPort);
        this.meuIp = NetworkUtils.getIpAddress();
        this.estoqueInicial = estoqueInicial;
    }

    public void iniciar() {
        try {
            RaftGroup raftGroup = montarGrupoRatis();
            this.peerIdStr = "n_" + portaInterna;
            RaftPeerId meuId = RaftPeerId.valueOf(peerIdStr);

            RaftProperties properties = new RaftProperties();
            File storageDir = new File("./ratis_storage/" + meuId);
            RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(storageDir));
            GrpcConfigKeys.Server.setPort(properties, portaInterna);

            this.stateMachine = new MyStateMachine();
            this.raftServer = RaftServer.newBuilder()
                    .setServerId(meuId)
                    .setStateMachine(stateMachine)
                    .setGroup(raftGroup)
                    .setProperties(properties)
                    .build();

            this.raftServer.start();
            System.out.println("Ratis Server iniciado na porta " + portaInterna);

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
                System.out.println(">>> [ESTOQUE] Este nó não possui estoque inicial configurado.");
                return;
            }

            for (Map.Entry<String, Integer> item : this.estoqueInicial.entrySet()) {
                String ingrediente = item.getKey();
                int qtd = item.getValue();

                String cmd = "ADD_ESTOQUE:" + peerIdStr + ":" + ingrediente + ":" + qtd;

                raftClient.io().send(Message.valueOf(cmd));
                System.out.println(">>> [ESTOQUE] Registrei " + qtd + "x " + ingrediente);
            }

        } catch (Exception e) {
            System.err.println("Erro ao registrar estoque: " + e.getMessage());
        }
    }

    private RaftGroup montarGrupoRatis() {
        String ipBase = NetworkUtils.getIpAddress();
        List<RaftPeer> peers = new ArrayList<>();
        for (int porta : PORTAS_DO_CLUSTER) {
            peers.add(RaftPeer.newBuilder()
                    .setId("n_" + porta)
                    .setAddress(ipBase + ":" + porta)
                    .build());
        }
        RaftGroupId groupId = RaftGroupId.valueOf(UUID.fromString("02511d47-d67c-49a3-9011-abb3109a44c1"));
        return RaftGroup.valueOf(groupId, peers);
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
                // erros ignorados
            }
        }
    }

    private void assumirLideranca() {
        this.souLider = true;
        System.out.println(">>> [EVENTO] VIREI LÍDER! Iniciando WebService...");
        try {
            if (endpointAtual != null && endpointAtual.isPublished()) {
                endpointAtual.stop();
            }

            MercadoServidorImpl implementacaoWs = new MercadoServidorImpl(this);
            String url = "http://" + this.meuIp + ":" + this.portaWebService + "/mercado";

            this.endpointAtual = Endpoint.publish(url, implementacaoWs);

            System.out.println("Web Service NO AR em: " + url);
            dnsClient.registrarSe("LIDER", this.portaWebService);

        } catch (Exception e) {
            e.printStackTrace();
            this.souLider = false;
        }
    }

    private void perderLideranca() {
        this.souLider = false;
        System.out.println(">>> [EVENTO] PERDI A LIDERANÇA.");
        if (endpointAtual != null) {
            endpointAtual.stop();
        }
    }

    public int processarCadastro(String restaurante) {
        if (!souLider) return -1;
        try {
            String comando = "CADASTRO:" + restaurante;
            RaftClientReply reply = raftClient.io().send(Message.valueOf(comando));
            return Integer.parseInt(reply.getMessage().getContent().toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return -1;
        }
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
}