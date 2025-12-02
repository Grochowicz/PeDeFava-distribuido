package network;

import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.raftlog.RaftLog;
import org.apache.ratis.statemachine.StateMachineStorage;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.proto.RaftProtos.LogEntryProto;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.impl.SingleFileSnapshotInfo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class MyStateMachine extends BaseStateMachine {

    private final Map<Integer, List<String>> pedidos = new HashMap<>();
    private final Map<Integer, String> pedidosRestaurante = new HashMap<>();
    private final AtomicInteger contadorPedidos = new AtomicInteger(0);

    private final Map<String, Map<String, Integer>> estoqueGlobal = new HashMap<>();

    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();

    private Node nodeRef;

    public void setNode(Node node) {
        this.nodeRef = node;
    }

    @Override
    public void initialize(RaftServer server, RaftGroupId groupId, RaftStorage raftStorage) throws IOException {
        super.initialize(server, groupId, raftStorage);
        this.storage.init(raftStorage);
        loadSnapshot(storage.getLatestSnapshot());
    }

    @Override
    public StateMachineStorage getStateMachineStorage() {
        return storage;
    }

    public boolean existeRestaurante(int id) {
        return pedidosRestaurante.containsKey(id);
    }

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        LogEntryProto entry = trx.getLogEntry();
        String comando = entry.getStateMachineLogEntry().getLogData().toString(StandardCharsets.UTF_8);
        String resposta = "ERRO";

        try {
            if (comando.startsWith("CADASTRO:")) {
                String nome = comando.split(":")[1];
                int novoId = contadorPedidos.incrementAndGet();
                pedidosRestaurante.put(novoId, nome);
                resposta = String.valueOf(novoId);

            } else if (comando.startsWith("ADD_ESTOQUE:")) {
                String[] p = comando.split(":");
                String nodeId = p[1];
                String item = p[2];
                int qtd = Integer.parseInt(p[3]);

                estoqueGlobal.computeIfAbsent(nodeId, k -> new HashMap<>())
                        .merge(item, qtd, Integer::sum);
                resposta = "OK";
                System.out.println("[SM] Estoque atualizado para " + nodeId + ": " + item + " += " + qtd);

            } else if (comando.startsWith("COMPRA:")) {
                String[] p = comando.split(":");
                int idRest = Integer.parseInt(p[1]);
                String[] itens = p[2].split(",");

                boolean sucessoTotal = true;
                List<String> consumidos = new ArrayList<>();

                for (String item : itens) {
                    if (consumirDeAlguem(item)) {
                        consumidos.add(item);
                    } else {
                        sucessoTotal = false;
                        break;
                    }
                }

                if (sucessoTotal) {
                    pedidos.computeIfAbsent(idRest, k -> new ArrayList<>()).addAll(Arrays.asList(itens));
                    resposta = "OK";
                } else {
                    for (String item : consumidos) {
                        devolverEstoque(item);
                    }
                    resposta = "FALHA: Sem estoque";
                }

            } else if (comando.startsWith("REQ_ADD_MEMBER:")) {
                if (nodeRef != null) {
                    String[] p = comando.split(":");
                    String novoId = p[1];
                    String novoIp = p[2];
                    int novaPorta = Integer.parseInt(p[3]);

                    new Thread(() -> nodeRef.adicionarNovoMembro(novoId, novoIp, novaPorta)).start();
                }
                resposta = "PROCESSANDO_ENTRADA";
            }

        } catch (Exception e) {
            e.printStackTrace();
            resposta = "ERRO: " + e.getMessage();
        }

        return CompletableFuture.completedFuture(Message.valueOf(resposta));
    }

    private boolean consumirDeAlguem(String item) {
        for (Map.Entry<String, Map<String, Integer>> nodeEntry : estoqueGlobal.entrySet()) {
            Map<String, Integer> estoqueDoNode = nodeEntry.getValue();
            if (estoqueDoNode.containsKey(item) && estoqueDoNode.get(item) > 0) {
                estoqueDoNode.put(item, estoqueDoNode.get(item) - 1);
                return true;
            }
        }
        return false;
    }

    private void devolverEstoque(String item) {
        if (!estoqueGlobal.isEmpty()) {
            String primeiroNode = estoqueGlobal.keySet().iterator().next();
            estoqueGlobal.get(primeiroNode).merge(item, 1, Integer::sum);
        }
    }

    @Override
    public long takeSnapshot() {
        TermIndex termIndex = getLastAppliedTermIndex();
        if (termIndex == null) {
            return RaftLog.INVALID_LOG_INDEX;
        }

        long lastIndex = termIndex.getIndex();
        File snapshotFile = storage.getSnapshotFile(termIndex.getTerm(), lastIndex);

        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(snapshotFile)))) {

            out.writeObject(pedidos);
            out.writeObject(pedidosRestaurante);
            out.writeObject(contadorPedidos);
            out.writeObject(estoqueGlobal);

            System.out.println("Snapshot criado com sucesso no index: " + lastIndex);

        } catch (IOException e) {
            e.printStackTrace();
            return RaftLog.INVALID_LOG_INDEX;
        }

        return lastIndex;
    }

    private void loadSnapshot(SingleFileSnapshotInfo snapshot) throws IOException {
        if (snapshot == null) {
            System.out.println("Nenhum snapshot encontrado. Iniciando vazio.");
            return;
        }

        File file = snapshot.getFile().getPath().toFile();
        if (!file.exists()) return;

        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {

            @SuppressWarnings("unchecked")
            Map<Integer, List<String>> p = (Map<Integer, List<String>>) in.readObject();
            pedidos.putAll(p);

            @SuppressWarnings("unchecked")
            Map<Integer, String> pr = (Map<Integer, String>) in.readObject();
            pedidosRestaurante.putAll(pr);

            AtomicInteger cp = (AtomicInteger) in.readObject();
            contadorPedidos.set(cp.get());

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Integer>> eg = (Map<String, Map<String, Integer>>) in.readObject();
            estoqueGlobal.putAll(eg);

            setLastAppliedTermIndex(snapshot.getTermIndex());
            System.out.println("Snapshot carregado! Estoque Global: " + estoqueGlobal);

        } catch (ClassNotFoundException e) {
            throw new IOException("Classe não encontrada ao ler snapshot", e);
        }
    }
}