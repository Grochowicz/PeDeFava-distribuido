import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Mesas {

    public static void main(String[] args) {
        //String host = (args.length < 1) ? null : args[0];

        // Cliente para restaurante
        try {
            Registry registry = LocateRegistry.getRegistry(2001);
            Restaurante stub = (Restaurante) registry.lookup("main.java.java.Restaurante");
            int id = stub.novaComanda("Eric",1);
            System.out.println(id);

            String[] cardapio = stub.consultarCardapio();

            for(String s:cardapio) {
                System.out.println(s);
            }

            System.out.println("Fazendo pedido");
            System.out.println(stub.fazerPedido(id, new String[]{cardapio[1], cardapio[2]}));

            System.out.println("Calculando o total");
            System.out.println(stub.valorComanda(id));

            while(!stub.fecharComanda(id)) {
                System.out.println("Pedido não está pronto.");
                Thread.sleep(1000);
            }
            System.out.println("Fechando comanda");


        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
