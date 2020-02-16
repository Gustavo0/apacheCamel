package br.com.caelum.camel;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		context.addComponent("activeMq", ActiveMQComponent.activeMQComponent("tcp://localhost:61616"));
		context.addRoutes(new RouteBuilder() {

			@Override
            public void configure() throws Exception {
				
				errorHandler(
				    //deadLetterChannel("file:erro")
				    deadLetterChannel("activemq:queue:pedidos.DLQ")
				        .maximumRedeliveries(3)
				            .redeliveryDelay(3000)
				        .onRedelivery(new Processor() {            
				            @Override
				            public void process(Exchange exchange) throws Exception {
				                        int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
				                        int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
				                        System.out.println("Redelivery - " + counter + "/" + max );
				                    }
				        })
				);
				
				//from("file:pedidos?delay=5s&noop=true")//O parâmetro noop=true significa que os arquivos não serão apagados da pasta pedidos
				from("activemq:queue:pedidos")
				.routeId("rota-pedidos")
				.delay(1000)
				.to("validator:pedido.xsd")
				.multicast()
					.to("direct:soap")
					.to("direct:http");
				
				from("direct:http")
					.routeId("rota-http")
			    	.setProperty("pedidoId", xpath("/pedido/id/text()"))
			    	.setProperty("clienteId", xpath("/pedido/pagamento/email-titular/text()"))
			    	.log("${property.clienteId}")
			    	.split()
			    		.xpath("/pedido/itens/item")
			    		//.log("${body}")
					.filter()
						.xpath("/item/formato[text()='EBOOK']")
					.setProperty("ebookId", xpath("/item/livro/codigo/text()"))
			    	.marshal()
			    		.xmljson()
			    		//.log("${body}")
			    	.setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
			    	.setHeader(Exchange.HTTP_QUERY, 
			                simple("clienteId=${property.clienteId}&pedidoId=${property.pedidoId}&ebookId=${property.ebookId}"))
			    .to("http4://localhost:8080/webservices/ebook/item");
				
				from("direct:soap")
					.routeId("rota-soap")
					.to("xslt:pedido-para-soap.xslt")
					.log("${body}")
					.setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
				.to("http4://localhost:8080/webservices/financeiro");
            }

		});
		
		context.start(); //aqui camel realmente começa a trabalhar
        Thread.sleep(20000); //esperando um pouco para dar um tempo para camel
        context.stop();

	}	
}
