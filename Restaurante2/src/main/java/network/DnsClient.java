package network;

import java.io.*;
import java.net.*;

public class DnsClient {
    private String dnsIp;
    private int dnsPort;

    public DnsClient(String dnsIp, int dnsPort) {
        this.dnsIp = dnsIp;
        this.dnsPort = dnsPort;
    }

    // NomeServico: Filial/Lider
    public void registrarSe(String nomeServico, int minhaPorta) {
        try (Socket socket = new Socket(dnsIp, dnsPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String meuIp = NetworkUtils.getIpAddress();
            out.println("REGISTRAR:" + nomeServico + ":" + meuIp + ":" + minhaPorta);

        } catch (IOException e) {
            System.err.println("Não foi possível registrar no DNS: " + e.getMessage());
        }
    }

    public String descobrirLider() {
        try (Socket socket = new Socket(dnsIp, dnsPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("GET_LIDER");
            String resposta = in.readLine();
            return resposta;

        } catch (IOException e) {
            return null;
        }
    }
}