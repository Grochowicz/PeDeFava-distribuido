package wsMercado;
import java.net.URL;
import network.DnsClient;
import network.NetworkUtils;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

public class MercadoMiddleware {
    private DnsClient dns;
    private MercadoServidor mercado;
    private int idMercado;
    private String ipLiderAtual;
    private boolean conectado;

    public MercadoMiddleware() {
        this(NetworkUtils.getIpAddress());
    }

    public MercadoMiddleware(String ipDnsServer) {
        this.dns = new DnsClient(ipDnsServer, 9090);
        this.conectado = false;
        System.out.println("Middleware apontando para DNS em: " + ipDnsServer);
    }

    private void conectarAoLider() throws Exception {
        String enderecoLider = dns.descobrirLider();

        if (enderecoLider == null || enderecoLider.startsWith("ERRO")) {
            throw new Exception("Mercado fora do ar");
        }

        if (conectado && enderecoLider.equals(ipLiderAtual)) {
            return;
        }

        URL wsdlURL = new URL("http://" + enderecoLider + "/mercado?wsdl");
        QName SERVICE_NAME = new QName("http://wsMercado/", "MercadoServidorImplService");
        Service service = Service.create(wsdlURL, SERVICE_NAME);

        mercado = service.getPort(new QName("http://wsMercado/", "MercadoServidorImplPort"), MercadoServidor.class);

        if (mercado == null) {
            throw new RuntimeException("Falha ao inicializar o Mercado via WS. Verifique URL e QName.");
        }

        if (!conectado) {
            idMercado = mercado.cadastrarPedido("java.PeDeFava");
            System.out.println("Conectado ao Mercado via WS! ID: " + idMercado);
        }

        ipLiderAtual = enderecoLider;
        conectado = true;
    }

    public boolean comprarProdutos(String item, int qtd) {
        try {
            conectarAoLider();

            String[] produtos = new String[qtd];
            for (int i = 0; i < qtd; i++) {
                produtos[i] = item;
            }

            return mercado.comprarProdutos(idMercado, produtos);
        } catch (Exception e) {
            System.err.println("Erro ao comprar produtos: " + e.getMessage());
            e.printStackTrace();
            conectado = false;
            return false;
        }
    }

    public int tempoEntrega() {
        try {
            if (!conectado) {
                conectarAoLider();
            }
            return mercado.tempoEntrega(idMercado);
        } catch (Exception e) {
            System.err.println("Erro ao obter tempo de entrega: " + e.getMessage());
            return -1;
        }
    }
}