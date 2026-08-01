package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication;

import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class KrakenRequestSigner {
    public SignedHeaders sign(String path,Map<String,String> body,CredentialMaterial credentials){
        char[] key=credentials.copyApiKey(),secret=credentials.copyApiSecret();
        try{
            String postData=body.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(Collectors.joining("&"));
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((body.get("nonce")+postData).getBytes(StandardCharsets.UTF_8));
            byte[] pathBytes=path.getBytes(StandardCharsets.UTF_8),message=new byte[pathBytes.length+hash.length];
            System.arraycopy(pathBytes,0,message,0,pathBytes.length);System.arraycopy(hash,0,message,pathBytes.length,hash.length);
            Mac mac=Mac.getInstance("HmacSHA512");mac.init(new SecretKeySpec(Base64.getDecoder().decode(new String(secret)),"HmacSHA512"));
            return new SignedHeaders(new String(key),Base64.getEncoder().encodeToString(mac.doFinal(message)));
        }catch(Exception e){throw new IllegalStateException("Unable to sign Kraken request",e);}
        finally{Arrays.fill(key,'\0');Arrays.fill(secret,'\0');}
    }
    private String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public record SignedHeaders(String apiKey,String signature){}
}
