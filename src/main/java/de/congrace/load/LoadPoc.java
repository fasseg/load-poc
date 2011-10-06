package de.congrace.load;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServerConnection;

import org.hyperic.sigar.CpuInfo;
import org.hyperic.sigar.CpuPerc;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;

import com.sun.management.OperatingSystemMXBean;

public class LoadPoc {
	public static void main(String[] args) {
		LoadPoc poc = new LoadPoc();
		try {
			
			// generate some load
			LoadGenerator gen=poc.new LoadGenerator();
			Thread t=new Thread(gen);
			t.start();
			while(!gen.isWorking()){
				Thread.yield();
			}
			// wait a while to read the results
			System.out.println("warming up cpu so load won't be 0...");
			Thread.sleep(1000);

			// and read the loads using the different methods
			poc.readLoadWithSigar();
			poc.readLoadOnLinux();
			poc.readLoadFromMBean();
			
			// stop the load thread.
			gen.setStop(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * need to cast java.lang.management.OperatingSystemMXBean to restricted
	 * com.sun.management.OperationgSystemMXBean works only for the instance?!
	 */
	private void readLoadFromMBean() throws Exception {
		DecimalFormat format = new DecimalFormat("#.##");
		System.out.println("\n-- MBEAN calculated load");
		MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
		OperatingSystemMXBean os = ManagementFactory.newPlatformMXBeanProxy(conn,
				ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
		//calculate a 5 sec average CPU load metric by hand
		final long nanoBefore = System.nanoTime();
		final long cpuBefore = os.getProcessCpuTime();
		Thread.sleep(5000);
		final long cpuAfter = os.getProcessCpuTime();
		final long nanoAfter = System.nanoTime();
		final double load = (cpuBefore == cpuAfter) ? 0d
				: ((double) (cpuAfter - cpuBefore) * 100d / (double) (nanoAfter - nanoBefore));
		
		
		System.out.println("CPU load: " + format.format(os.getSystemCpuLoad()) + "%");
		System.out.println("Process load: " + format.format(os.getProcessCpuLoad()) + "%");
		System.out.println("Calculated five sec average: " + format.format(load) + "%");
	}

	/*
	 * works only on linux by reading "/proc/loadavg"
	 */
	private void readLoadOnLinux() throws Exception {
		String os=System.getProperty("os.name");
		if (!(os.indexOf( "nix") >=0 || os.indexOf( "nux") >=0)){
			System.out.println("unable to run linux test in your OS: " + os);
		}
		InputStream in = null;
		try {
			System.out.println("\n-- LINUX /proc/loadavg");
			in = new FileInputStream("/proc/loadavg");
			final byte[] buf = new byte[1024];
			int bytesRead = 0;
			StringBuilder loadBuilder = new StringBuilder();
			while ((bytesRead = in.read(buf)) != -1) {
				loadBuilder.append(new String(buf, 0, bytesRead));
			}
			String[] data = loadBuilder.toString().split(" ");
			System.out.println("last minute utilization " + data[0]);
			System.out.println("last 5 minute utilization " + data[1]);
			System.out.println("last 15 minute utilization " + data[2]);
			System.out.println("currently running processes " + data[3]);
			System.out.println("toal number of processes: " + data[4]);
			System.out.println();
		} finally {
			in.close();
		}
	}

	/*
	 * you will have to install the sigar binary library for the according
	 * system you are running this method on. get the libraries at e.g.:
	 * http://svn.hyperic.org/projects/sigar_bin/dist/SIGAR_1_6_5/lib/
	 */
	private void readLoadWithSigar() throws IOException, SigarException, InterruptedException {
		System.out.println("\n-- SIGAR load information");
		final Sigar sigar = new Sigar();
		final CpuInfo cpu = sigar.getCpuInfoList()[0];
		System.out.println("MODEL: " + cpu.getModel());
		System.out.println("FREQ: " + cpu.getMhz());
		System.out.println("CACHE: " + cpu.getCacheSize());
		System.out.println("CORES:" + cpu.getCoresPerSocket());
		int count = 1;
		for (CpuPerc perc : sigar.getCpuPercList()) {
			System.out.println("-- CPU [" + (count++) + "] --");
			System.out.println("idle: " + perc.getIdle());
			System.out.println("sys: " + perc.getSys());
			System.out.println("user: " + perc.getUser());
		}
	}

	private class LoadGenerator implements Runnable {
		private AtomicBoolean stop = new AtomicBoolean(false);
		private boolean working=false;

		private LoadGenerator() {
		}

		@Override
		public void run() {
			Random rnd=new Random();
			final List<Double> data=new LinkedList<Double>();
			for (int i=0;i<1024*1024;i++){
				data.add(rnd.nextDouble());
			}
			
			working=true;
			while (!stop.get()){
				Collections.shuffle(data);
				Collections.sort(data);
			}
		}

		boolean isStop() {
			return stop.get();
		}

		void setStop(boolean stop) {
			this.stop.set(stop);
		}

		public boolean isWorking() {
			return working;
		}
	}
}
