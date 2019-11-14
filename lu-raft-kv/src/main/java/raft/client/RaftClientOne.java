package raft.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.remoting.exception.RemotingException;

import cn.think.in.java.current.SleepHelper;
import cn.think.in.java.entity.LogEntry;
import cn.think.in.java.rpc.DefaultRpcClient;
import cn.think.in.java.rpc.Request;
import cn.think.in.java.rpc.Response;
import cn.think.in.java.rpc.RpcClient;

/**
 *
 * @author 莫那·鲁道
 */
public class RaftClientOne {

    private static final Logger LOGGER = LoggerFactory.getLogger(RaftClientOne.class);


    private final static RpcClient client = new DefaultRpcClient();

    static String addr = "localhost:8778";
    
    public static void main(String[] args) throws RemotingException, InterruptedException {
 
        ClientKVReq obj = ClientKVReq.newBuilder().key("hello").value("world").type(ClientKVReq.PUT).build();

        Request<ClientKVReq> r = new Request<>();
        r.setObj(obj);
        r.setUrl(addr);
        r.setCmd(Request.CLIENT_REQ);
        Response<String> response = null;
        try {
            response = client.send(r);
        } catch (Exception e) {
            e.printStackTrace();
        }

        LOGGER.info("request content : {}, url : {}, put response : {}", obj.key + "=" + obj.getValue(), r.getUrl(), response.getResult());

        SleepHelper.sleep(1000);

        obj = ClientKVReq.newBuilder().key("hello").type(ClientKVReq.GET).build(); 
        r.setUrl(addr);
        r.setObj(obj);

        Response<LogEntry> response2 = null;
        try {
            response2 = client.send(r);
        } catch (Exception e) {
           e.printStackTrace();
        } 
        LOGGER.info("request content : {}, url : {}, get response : {}", obj.key + "=" + obj.getValue(), r.getUrl(), response2.getResult());
     
      
    } 
}
