import network.Node;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        int porta = 0;
        Map<String, Integer> estoque = new HashMap<>();

        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);

                for (int i = 1; i < args.length; i++) {
                    String param = args[i]; // "Pó de Café=50"

                    if (param.contains("=")) {
                        String[] partes = param.split("=");
                        String nomeItem = partes[0].trim();
                        int quantidade = Integer.parseInt(partes[1].trim());

                        estoque.put(nomeItem, quantidade);
                    }
                }
            } catch (Exception e) {
                System.err.println("Erro ao ler argumentos: " + e.getMessage());
                System.exit(1);
            }
        }

        else {
            Scanner scanner = new Scanner(System.in);

            System.out.println("=== CONFIGURAÇÃO MANUAL DO NÓ ===");

            System.out.print("Informe a porta deste nó (ex: 3000): ");
            while (porta == 0) {
                try {
                    String input = scanner.nextLine();
                    porta = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.out.print("Número inválido. Tente novamente: ");
                }
            }

            System.out.println("\n--- CADASTRO DE ESTOQUE ---");
            System.out.println("Digite os itens no formato: Nome=Quantidade");
            System.out.println("Exemplo: Café=50");
            System.out.println("Digite 'FIM' ou deixe vazio para encerrar e iniciar o nó.");

            while (true) {
                System.out.print("> ");
                String linha = scanner.nextLine().trim();

                if (linha.isEmpty() || linha.equalsIgnoreCase("FIM")) {
                    break;
                }

                if (linha.contains("=")) {
                    try {
                        String[] partes = linha.split("=");
                        String nome = partes[0].trim();
                        int qtd = Integer.parseInt(partes[1].trim());
                        estoque.put(nome, qtd);
                        System.out.println("   + Adicionado: " + nome + " (" + qtd + ")");
                    } catch (Exception e) {
                        System.out.println("   ! Erro de formato. Use Nome=Quantidade");
                    }
                } else {
                    System.out.println("   ! Formato incorreto. Faltou o '='.");
                }
            }
        }

        System.out.println("\n>>> Inicializando Node na porta " + porta);
        if (estoque.isEmpty()) {
            System.out.println(">>> AVISO: Nenhum estoque inicial configurado.");
        } else {
            System.out.println(">>> Estoque inicial: " + estoque);
        }

        String ipDns = "127.0.0.1";
        int portaDns = 9090;

        Node node = new Node(ipDns, portaDns, porta, estoque);
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