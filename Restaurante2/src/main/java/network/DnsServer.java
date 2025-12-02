package network;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class DnsServer {
    // "LIDER" -> IP
    private static ConcurrentHashMap<String, String> servicos = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int portaDns = 9090;
        System.out.println("DNS Server rodando na porta " + portaDns);

        try (ServerSocket serverSocket = new ServerSocket(portaDns)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> tratarRequisicao(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void tratarRequisicao(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String mensagem = in.readLine();

            if (mensagem.startsWith("REGISTRAR:")) {
                String[] partes = mensagem.split(":");
                String tipo = partes[1]; // LIDER ou não
                String endereco = partes[2] + ":" + partes[3];

                servicos.put(tipo, endereco);
                System.out.println("Registrado: " + tipo + " em " + endereco);
                out.println("OK");

            } else if (mensagem.equals("GET_LIDER")) {
                String enderecoLider = servicos.get("LIDER");
                if (enderecoLider != null) {
                    out.println(enderecoLider); // IP do lider
                } else {
                    out.println("ERRO: LIDER_NAO_ENCONTRADO");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}