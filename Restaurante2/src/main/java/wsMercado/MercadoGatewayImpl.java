package wsMercado;

import network.DnsClient;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.URL;

@WebService(endpointInterface = "wsMercado.MercadoServidor")
public class MercadoGatewayImpl implements MercadoServidor {

    private DnsClient dns;

    public MercadoGatewayImpl(String ipDns) {
        this.dns = new DnsClient(ipDns, 9090);
    }

    private MercadoServidor conectarNoLiderReal() throws Exception {
        String enderecoLider = dns.descobrirLider();

        if (enderecoLider == null || enderecoLider.startsWith("ERRO")) {
            throw new Exception("Cluster indisponível (DNS não achou líder).");
        }

        System.out.println("[Gateway] Tentando conectar no líder: " + enderecoLider);

        try {
            URL wsdlURL = new URL("http://" + enderecoLider + "/mercado?wsdl");
            QName qname = new QName("http://wsMercado/", "MercadoServidorImplService");
            Service service = Service.create(wsdlURL, qname);
            return service.getPort(MercadoServidor.class);
        } catch (Exception e) {
            throw new Exception("Falha ao conectar no líder (possível reeleição em curso): " + e.getMessage());
        }
    }

    @Override
    public int cadastrarPedido(String restaurante) {
        try {
            return conectarNoLiderReal().cadastrarPedido(restaurante);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public boolean comprarProdutos(int idRestaurante, String[] produtos) {
        try {
            return conectarNoLiderReal().comprarProdutos(idRestaurante, produtos);
        } catch (Exception e) {
            System.err.println("[Gateway] Erro ao contatar líder: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int tempoEntrega(int idRestaurante) {
        try {
            return conectarNoLiderReal().tempoEntrega(idRestaurante);
        } catch (Exception e) {
            return -1;
        }
    }
}
