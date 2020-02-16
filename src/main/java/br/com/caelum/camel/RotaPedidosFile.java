package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidosFile {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		context.addRoutes(new RouteBuilder() {

			@Override
            public void configure() throws Exception {
				from("file:pedidos?delay=5s&noop=true")//O par�metro noop=true significa que os arquivos n�o ser�o apagados da pasta pedidos
			    	.split()
			    		.xpath("/pedido/itens/item")
			    		.log("${body}")
					.filter()
						.xpath("/item/formato[text()='EBOOK']")
			    	.marshal()
			    		.xmljson()
			    		.log("${body}")
			    	.setHeader(Exchange.FILE_NAME, simple("${file:name.noext}-${header.CamelSplitIndex}.json"))
			    .to("file:saida");
            }

		});
		
		context.start(); //aqui camel realmente come�a a trabalhar
        Thread.sleep(20000); //esperando um pouco para dar um tempo para camel
        context.stop();

	}	
}
