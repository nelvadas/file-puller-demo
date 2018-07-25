package com.redhat.training.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class IdempotentKeyProcessor implements Processor {

	@Override
	public void process(Exchange exchange) throws Exception {
		
		//retreive the Order Data
		String orderLine= exchange.getIn().getBody(String.class);
		
		//get the OrderID
		String orderId=orderLine.split(",")[0];
		
		//get the filename
		String idempotentKey=exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY)+"-"+ orderId;
		
		exchange.getIn().setHeader("yrOrderIdempotentKey", idempotentKey);

	}

}
