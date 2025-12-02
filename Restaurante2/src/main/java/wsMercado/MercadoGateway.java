package wsMercado;

import javax.xml.ws.Endpoint;

public class MercadoGateway {

    public static void main(String[] args) {
        String ipDns = "127.0.0.1";

        String url = "http://0.0.0.0:9999/mercadoGateway";

        Endpoint.publish(url, new MercadoGatewayImpl(ipDns));

        System.out.println("GATEWAY RODANDO EM: " + url + "?wsdl");
    }
}