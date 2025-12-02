import network.Node;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        int minhaPorta = 0;
        String ipDns = "127.0.0.1"; // Padrão localhost
        int portaDns = 9090;        //  DNS
        Map<String, Integer> estoque = new HashMap<>();

        System.out.println("   INICIALIZADOR DO NÓ (AUTO-JOIN)");

        if (args.length > 0) {
            try {
                minhaPorta = Integer.parseInt(args[0]);

                int indiceInicioEstoque = 1;

                if (args.length > 1) {
                    String arg1 = args[1];
                    if (!arg1.contains("=")) {
                        ipDns = arg1;
                        indiceInicioEstoque = 2;
                    }
                }

                for (int i = indiceInicioEstoque; i < args.length; i++) {
                    String param = args[i];
                    if (param.contains("=")) {
                        String[] partes = param.split("=");
                        String key = partes[0].trim();
                        int val = Integer.parseInt(partes[1].trim());
                        estoque.put(key, val);
                    }
                }

            } catch (Exception e) {
                System.err.println("Erro ao ler argumentos: " + e.getMessage());
                System.out.println("Uso: java Main <PORTA> [IP_DNS] <ITEM=QTD>...");
                System.exit(1);
            }
        }

        else {
            Scanner scanner = new Scanner(System.in);

            System.out.print("Informe a SUA porta (ex: 3000): ");
            while (minhaPorta == 0) {
                try {
                    minhaPorta = Integer.parseInt(scanner.nextLine());
                } catch (Exception e) {
                    System.out.print("Inválido. Tente novamente: ");
                }
            }

            System.out.print("Informe o IP do DNS (Enter para " + ipDns + "): ");
            String inputDns = scanner.nextLine().trim();
            if (!inputDns.isEmpty()) {
                ipDns = inputDns;
            }

            System.out.println("\n--- CADASTRO DE ESTOQUE ---");
            System.out.println("Digite 'Item=Quantidade' (ex: Café=50).");
            System.out.println("Digite 'FIM' ou Enter vazio para iniciar.");

            while (true) {
                System.out.print("> ");
                String linha = scanner.nextLine().trim();
                if (linha.isEmpty() || linha.equalsIgnoreCase("FIM")) break;

                if (linha.contains("=")) {
                    try {
                        String[] partes = linha.split("=");
                        estoque.put(partes[0].trim(), Integer.parseInt(partes[1].trim()));
                    } catch (Exception e) {
                        System.out.println("Formato inválido.");
                    }
                }
            }
        }

        System.out.println("\n>>> CONFIGURAÇÃO FINAL:");
        System.out.println("   - Porta Local: " + minhaPorta);
        System.out.println("   - DNS Server : " + ipDns + ":" + portaDns);
        System.out.println("   - Estoque    : " + estoque);
        System.out.println("-----------------------------------------");

        Node node = new Node(ipDns, portaDns, minhaPorta, estoque, new ArrayList<>());
        node.iniciar();

        try {
            Object lock = new Object();
            synchronized (lock) {
                lock.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}