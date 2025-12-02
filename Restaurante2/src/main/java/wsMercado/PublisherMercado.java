package wsMercado;

import javax.xml.ws.Endpoint;

public class PublisherMercado {
    public static void main(String[] args) {
        String url = "http://localhost:8900/wsMercado";

        Endpoint.publish(url, new MercadoServidorImpl());

        System.out.println("Serviço do Mercado rodando em: " + url);
    }
}
