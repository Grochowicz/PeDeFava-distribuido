import java.rmi.*;
/*
 * CSV (int,string,float):
 * codigo,produto,valor
 */
public interface Restaurante extends Remote {
    /* Retorna o número da nova comanda para um novo cliente (String nome) */
    public int novaComanda(String nome, int mesa) throws RemoteException;

    /* Consulta as opções disponíveis no cardápio.
     * Formato do retorno: CSV */
    public String[] consultarCardapio() throws RemoteException;

    /* Para uma determinada comanda, recebe um vetor de pedidos.
     * Formato do pedido: CSV */
    public String fazerPedido(int comanda, String[] pedido) throws RemoteException;
    /* Solicita o valor total para pagamento */

    public float valorComanda(int comanda) throws RemoteException;

    /* Realiza o pagamento e libera */
    public boolean fecharComanda(int comanda) throws RemoteException;
}