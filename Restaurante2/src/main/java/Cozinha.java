import java.rmi.*;
/*
 * CSV (int,string,float):
 * codigo,produto,valor
 */
public interface Cozinha extends Remote {
    /* Cadastra um novo preparo e retorna o codigo */
    public int novoPreparo(int comanda, String[] pedido) throws RemoteException;

    /* Consulta tempo de espera do preparo (em segundos)
     * Tempo: random, entre 1 e 10 segundos.
     */
    public int tempoPreparo(int preparo) throws RemoteException;

    /* Busca o pedido preparado para entrega.
     * Somente pode buscar após o tempo combinado.
     */
    public String[] pegarPreparo(int preparo) throws RemoteException;
}