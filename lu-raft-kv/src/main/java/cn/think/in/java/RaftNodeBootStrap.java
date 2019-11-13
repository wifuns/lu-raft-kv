package cn.think.in.java;

import java.util.Arrays;

import cn.think.in.java.common.NodeConfig;
import cn.think.in.java.impl.DefaultNode;
import cn.think.in.java.impl.DefaultStateMachine;
import cn.think.in.java.util.PortUtil;

/**
 * -DserverPort=8775
 * -DserverPort=8776
 * -DserverPort=8777
 * -DserverPort=8778
 * -DserverPort=8779
 */
public class RaftNodeBootStrap {

    public static void main(String[] args) throws Throwable {
    	String[] peerAddr = {"localhost:8775","localhost:8776","localhost:8777", "localhost:8778", "localhost:8779"};
		 
    	for(String peer : peerAddr){
    		new Thread(new Server(peer.split(":")[1],peerAddr)).start();
    	}
    	//下面的方法是不对的。因为是线程池，那么线程就是固定的，我们要实现每个端口一个线程
		/*CompletableFuture<Void>[] deptFuture = Arrays.stream(peerAddr).map(group -> CompletableFuture.runAsync(
       	 	new Server(group.split(":")[1],peerAddr))).toArray( size -> new CompletableFuture[size]);
		CompletableFuture.allOf(deptFuture).get();*/
    } 
    
    static class Server implements Runnable{
    	 
    	public Server(String port, String[] peerAddr){
    		this.port = port;
    		this.peerAddr = peerAddr;
    	}
    	
    	String port;
    	String[] peerAddr;
		@Override
		public void run(){
			
	        NodeConfig config = new NodeConfig();
	        PortUtil.setPort(Integer.valueOf(port));
	        // 自身节点
	        config.setSelfPort(Integer.valueOf(port));

	        // 其他节点地址
	        config.setPeerAddrs(Arrays.asList(peerAddr));

	        Node node = DefaultNode.getInstance();
	        node.setConfig(config);

	        try {
				node.init();
			} catch (Throwable e) { 
				e.printStackTrace();
			}

	        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
	            try {
	                node.destroy();
	            } catch (Throwable throwable) {
	                throwable.printStackTrace();
	            }
	        }));  
		} 
    } 
}
