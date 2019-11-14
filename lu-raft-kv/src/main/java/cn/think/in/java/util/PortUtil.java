package cn.think.in.java.util;

public class PortUtil {
	// 创建线程局部变量，用来保存port
	public static final ThreadLocal<Integer> port = new ThreadLocal<>();
	public static final ThreadLocal<String> peer = new ThreadLocal<>();
 
	public static int currentPort() {
		return port.get(); 
	}

	public static void setPort(Integer p){
		port.set(p);
	}
	public static String peer() {
		return peer.get(); 
	}

	public static void setPeer(String p){
		peer.set(p);
	}
}
