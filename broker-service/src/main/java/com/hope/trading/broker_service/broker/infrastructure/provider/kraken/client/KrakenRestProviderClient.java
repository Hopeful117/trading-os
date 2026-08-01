package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.*;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.KrakenRequestSigner;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import org.slf4j.MDC;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;

@Component("krakenRestProviderClient")
public final class KrakenRestProviderClient implements KrakenProviderClient {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
    private static final Logger log=LoggerFactory.getLogger(KrakenRestProviderClient.class);
    private final RestClient client; private final KrakenRequestSigner signer;
    private final AtomicLong nonce=new AtomicLong(System.currentTimeMillis());
    public KrakenRestProviderClient(RestClient krakenRestClient,KrakenRequestSigner signer){this.client=krakenRestClient;this.signer=signer;}
    public JsonNode privatePost(String path,Map<String,String> parameters,CredentialMaterial credentials){
        Map<String,String> signed=new LinkedHashMap<>();signed.put("nonce",Long.toString(nonce.updateAndGet(previous->Math.max(previous+1,System.currentTimeMillis()))));signed.putAll(parameters);
        var headers=signer.sign(path,signed,credentials);var body=new LinkedMultiValueMap<String,String>();signed.forEach(body::add);
        long started=System.nanoTime();try{
            String payload=client.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("API-Key",headers.apiKey()).header("API-Sign",headers.signature())
                    .headers(httpHeaders->{String correlationId=MDC.get("correlationId");if(correlationId!=null)httpHeaders.set("X-Correlation-ID",correlationId);})
                    .body(body).retrieve().body(String.class);
            if(payload==null||payload.isBlank())throw new BrokerProtocolException("Empty Kraken response");
            JsonNode response=JSON.readTree(payload);
            translateErrors(response.path("error"));log.info("broker_provider_call provider=KRAKEN operation={} result=SUCCESS durationNanos={}",path,System.nanoTime()-started);return response.path("result");
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new BrokerProtocolException("Malformed Kraken response");}
         catch(ResourceAccessException e){throw new BrokerUnavailableException("Kraken communication failed",e);}
          catch(HttpClientErrorException.TooManyRequests e){throw new BrokerRateLimitException("Kraken rate limit exceeded");}
          catch(HttpClientErrorException.Unauthorized|HttpClientErrorException.Forbidden e){throw new BrokerAuthenticationException("Kraken authentication failed");}
          catch(HttpServerErrorException e){throw new BrokerUnavailableException("Kraken service unavailable",e);}
          catch(RestClientException e){throw new UnknownBrokerException("Unexpected Kraken client failure",e);}
          finally{log.debug("broker_provider_call_completed provider=KRAKEN operation={} durationNanos={}",path,System.nanoTime()-started);}
    }
    private void translateErrors(JsonNode errors){
        if(!errors.isArray()||errors.isEmpty())return;String code=errors.get(0).asText();
        String normalized=code.toLowerCase(Locale.ROOT);
        if(normalized.contains("invalid key")||normalized.contains("invalid signature"))throw new BrokerAuthenticationException(code);
        if(normalized.contains("permission denied"))throw new BrokerAuthorizationException(code);
        if(code.contains("Rate limit")||code.contains("Throttled"))throw new BrokerRateLimitException(code);
        if(normalized.contains("insufficient funds")||normalized.contains("insufficient margin"))throw new InsufficientFundsException(code);
        if(normalized.contains("unknown order")||normalized.contains("order not found"))throw new BrokerOrderNotFoundException(code);
        if(normalized.contains("invalid arguments")||normalized.contains("invalid volume")||normalized.contains("invalid price")||normalized.contains("unknown asset pair"))throw new InvalidOrderException(code);
        throw new BrokerProtocolException(code);
    }
}
