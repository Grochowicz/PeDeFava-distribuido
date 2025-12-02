package network;

public class TesteRede {
    public static void main(String[] args) {
        DnsClient client = new DnsClient("127.0.0.1", 9090);

        client.registrarSe("LIDER", 5000);

        String lider = client.descobrirLider();
        System.out.println("O líder está em: " + lider);
    }
}
