package org.cache.sync.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * IP 工具类
 * @since 2026/4/8
 */
public class IpUtils {

	private static final String KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
	private static final String POD_IP_ENV = "POD_IP";
	private static final String MY_POD_IP_ENV = "MY_POD_IP";
	private static final String HOST_IP_ENV = "HOST_IP";
	private static final String K8S_SERVICE_ACCOUNT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";

	/**
	 * 获取本机 IP 地址
	 * 如果在 Kubernetes Pod 环境中，优先返回 Pod IP
	 * 否则返回本地网卡 IP
	 *
	 * @return IP 地址
	 * @throws UnknownHostException 如果无法获取 IP
	 */
	public static String getLocalHost() throws UnknownHostException {
		// 1. 尝试从环境变量获取 Pod IP
		String podIp = getPodIpFromEnv();
		if (podIp != null && !podIp.isEmpty()) {
			return podIp;
		}

		// 2. 检测是否在 K8s 环境中，尝试获取 Pod IP
		if (isInKubernetesPod()) {
			podIp = getPodIpFromNetwork();
			if (podIp != null && !podIp.isEmpty()) {
				return podIp;
			}
		}

		// 3. 返回本地 IP
		return getLocalIpAddress();
	}

	/**
	 * 从环境变量中获取 Pod IP
	 */
	private static String getPodIpFromEnv() {
		// 优先检查常见的 Pod IP 环境变量
		String podIp = System.getenv(POD_IP_ENV);
		if (podIp != null && !podIp.isEmpty()) {
			return podIp;
		}

		podIp = System.getenv(MY_POD_IP_ENV);
		if (podIp != null && !podIp.isEmpty()) {
			return podIp;
		}

		podIp = System.getenv(HOST_IP_ENV);
		if (podIp != null && !podIp.isEmpty()) {
			return podIp;
		}

		return null;
	}

	/**
	 * 检测是否运行在 Kubernetes Pod 中
	 */
	private static boolean isInKubernetesPod() {
		// 方法1: 检查 K8s 服务环境变量
		String k8sServiceHost = System.getenv(KUBERNETES_SERVICE_HOST);
		if (k8sServiceHost != null && !k8sServiceHost.isEmpty()) {
			return true;
		}

		// 方法2: 检查 K8s ServiceAccount 文件是否存在
		try {
			java.nio.file.Path path = java.nio.file.Paths.get(K8S_SERVICE_ACCOUNT_PATH);
			return java.nio.file.Files.exists(path);
		} catch (Exception e) {
			// 忽略异常
		}

		return false;
	}

	/**
	 * 从网络接口获取 Pod IP
	 * 在 K8s 环境中，Pod IP 通常是 eth0 网卡的 IP
	 */
	private static String getPodIpFromNetwork() {
		try {
			// 遍历所有网络接口
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();

				// 跳过回环接口和未启用的接口
				if (networkInterface.isLoopback() || !networkInterface.isUp()) {
					continue;
				}

				// 在 K8s 中，Pod 的主网卡通常是 eth0
				String interfaceName = networkInterface.getName();
				if ("eth0".equals(interfaceName)) {
					Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
					while (addresses.hasMoreElements()) {
						InetAddress addr = addresses.nextElement();
						// 返回 IPv4 地址
						if (addr instanceof java.net.Inet4Address) {
							return addr.getHostAddress();
						}
					}
				}
			}
		} catch (Exception e) {
			// 忽略异常，返回 null
		}

		return null;
	}

	/**
	 * 获取本地 IP 地址
	 */
	private static String getLocalIpAddress() throws UnknownHostException {
		try {
			// 尝试获取非回环、非站点本地的 IPv4 地址
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();

				if (networkInterface.isLoopback() || !networkInterface.isUp()) {
					continue;
				}

				Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress addr = addresses.nextElement();

					if (addr instanceof java.net.Inet4Address && !addr.isSiteLocalAddress()) {
						return addr.getHostAddress();
					}
				}
			}

			// 如果没有找到公网 IP，返回第一个可用的 IPv4 地址
			interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();

				if (networkInterface.isLoopback() || !networkInterface.isUp()) {
					continue;
				}

				Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress addr = addresses.nextElement();
					if (addr instanceof java.net.Inet4Address) {
						return addr.getHostAddress();
					}
				}
			}

		} catch (Exception e) {
			// 忽略异常，使用默认方法
		}

		// 兜底：使用传统方法
		return InetAddress.getLocalHost().getHostAddress();
	}
}
