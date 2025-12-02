package wsMercado;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

@WebService(targetNamespace = "http://mercado.meuprojeto.com/")
@SOAPBinding(style = SOAPBinding.Style.RPC)
public interface MercadoServidor {
    @WebMethod
    int cadastrarPedido(String restaurante);
    @WebMethod
    boolean comprarProdutos(int restaurante, String[] produtos);
    @WebMethod
    int tempoEntrega(int restaurante);
}
