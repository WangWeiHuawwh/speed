// Copyright (c) 2015 D1SM.net

package net.fs.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;


import org.pcap4j.core.Pcaps;

import net.fs.rudp.Route;
import net.fs.utils.LogOutputStream;
import net.fs.utils.MLog;
import net.fs.utils.Tools;

import com.alibaba.fastjson.JSONObject;

public class ClientUI implements ClientUII {

    MapClient mapClient;


    ClientConfig config = null;

    String configFilePath = "client_config.json";

    String logoImg = "img/offline.png";

    String offlineImg = "img/offline.png";

    String name = "FinalSpeed";


    int serverVersion = -1;

    int localVersion = 2;

    boolean checkingUpdate = false;

    String domain = "";

    String homeUrl;

    public static ClientUI ui;


    boolean ky = true;

    String errorMsg = "保存失败请检查输入信息!";

//    public MapRuleListTable tcpMapRuleListTable;

    boolean capSuccess = false;
    Exception capException = null;
    boolean b1 = false;

    boolean success_firewall_windows = true;

    boolean success_firewall_osx = true;

    String systemName = null;

    public boolean osx_fw_pf = false;

    public boolean osx_fw_ipfw = false;

    public boolean isVisible = true;


    String updateUrl;
    
    boolean min=false;
    
    LogOutputStream los;
    
    boolean tcpEnable=true;

    {
        domain = "ip4a.com";
        homeUrl = "http://www.ip4a.com/?client_fs";
        updateUrl = "http://fs.d1sm.net/finalspeed/update.properties";
    }

    ClientUI(final boolean isVisible,boolean min) {
    	this.min=min;
        setVisible(isVisible);
        
        if(isVisible){
        	 los=new LogOutputStream(System.out);
             System.setOut(los);
             System.setErr(los);
        }
        
        
        systemName = System.getProperty("os.name").toLowerCase();
        MLog.info("System: " + systemName + " " + System.getProperty("os.version"));
        ui = this;
        ui = this;
        checkQuanxian();
        loadConfig();


        String server_addressTxt = config.getServerAddress();
        if (config.getServerAddress() != null && !config.getServerAddress().equals("")) {
            if (config.getServerPort() != 150
                    && config.getServerPort() != 0) {
                server_addressTxt += (":" + config.getServerPort());
            }
        }


        if (config.getRemoteAddress() != null && !config.getRemoteAddress().equals("") && config.getRemotePort() > 0) {
            String remoteAddressTxt = config.getRemoteAddress() + ":" + config.getRemotePort();
        }

        int width = 500;
        if (systemName.contains("os x")) {
            width = 600;
        }
        //mainFrame.setSize(width, 380);


        boolean tcpEnvSuccess=true;
        checkFireWallOn();
        if (!success_firewall_windows) {
        	tcpEnvSuccess=false;

            MLog.println("启动windows防火墙失败,请先运行防火墙服务.");
           // System.exit(0);
        }
        if (!success_firewall_osx) {
        	tcpEnvSuccess=false;

            MLog.println("启动ipfw/pf防火墙失败,请先安装.");
            //System.exit(0);
        }

        Thread thread = new Thread() {
            public void run() {
                try {
                    Pcaps.findAllDevs();
                    b1 = true;
                } catch (Exception e3) {
                    e3.printStackTrace();

                }
            }
        };
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        //JOptionPane.showMessageDialog(mainFrame,System.getProperty("os.name"));
        if (!b1) {
        	tcpEnvSuccess=false;

                        String msg = "启动失败,请先安装libpcap,否则无法使用tcp协议";
                        if (systemName.contains("windows")) {
                            msg = "启动失败,请先安装winpcap,否则无法使用tcp协议";
                        }

                        MLog.println(msg);
                        if (systemName.contains("windows")) {
                            try {
                                Process p = Runtime.getRuntime().exec("winpcap_install.exe", null);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            tcpEnable=false;
                            //System.exit(0);
                        }

        }


        try {
            mapClient = new MapClient(this,tcpEnvSuccess);
        } catch (final Exception e1) {
            e1.printStackTrace();
            capException = e1;
            //System.exit(0);
        }


        mapClient.setUi(this);

        mapClient.setMapServer(config.getServerAddress(), config.getServerPort(), config.getRemotePort(), null, null, config.isDirect_cn(), config.getProtocal().equals("tcp"),
                null);

        Route.es.execute(new Runnable() {

            @Override
            public void run() {
                checkUpdate();
            }
        });

        setSpeed(config.getDownloadSpeed(), config.getUploadSpeed());
    }

    void checkFireWallOn() {
        if (systemName.contains("os x")) {
            String runFirewall = "ipfw";
            try {
                final Process p = Runtime.getRuntime().exec(runFirewall, null);
                osx_fw_ipfw = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            runFirewall = "pfctl";
            try {
                final Process p = Runtime.getRuntime().exec(runFirewall, null);
                osx_fw_pf = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            success_firewall_osx = osx_fw_ipfw | osx_fw_pf;
        } else if (systemName.contains("linux")) {
            String runFirewall = "service iptables start";

        } else if (systemName.contains("windows")) {
            String runFirewall = "netsh advfirewall set allprofiles state on";
            Thread standReadThread = null;
            Thread errorReadThread = null;
            try {
                final Process p = Runtime.getRuntime().exec(runFirewall, null);
                standReadThread = new Thread() {
                    public void run() {
                        InputStream is = p.getInputStream();
                        BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
                        while (true) {
                            String line;
                            try {
                                line = localBufferedReader.readLine();
                                if (line == null) {
                                    break;
                                } else {
                                    if (line.contains("Windows")) {
                                        success_firewall_windows = false;
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                //error();
                                exit();
                                break;
                            }
                        }
                    }
                };
                standReadThread.start();

                errorReadThread = new Thread() {
                    public void run() {
                        InputStream is = p.getErrorStream();
                        BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
                        while (true) {
                            String line;
                            try {
                                line = localBufferedReader.readLine();
                                if (line == null) {
                                    break;
                                } else {
                                    System.out.println("error" + line);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                //error();
                                exit();
                                break;
                            }
                        }
                    }
                };
                errorReadThread.start();
            } catch (IOException e) {
                e.printStackTrace();
                success_firewall_windows = false;
                //error();
            }

            if (standReadThread != null) {
                try {
                    standReadThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (errorReadThread != null) {
                try {
                    errorReadThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    void checkQuanxian() {
        if (systemName.contains("windows")) {
            boolean b = false;
            File file = new File(System.getenv("WINDIR") + "\\test.file");
            //System.out.println("kkkkkkk "+file.getAbsolutePath());
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            b = file.exists();
            file.delete();

            if (!b) {
                //mainFrame.setVisible(true);

                MLog.println("请以管理员身份运行,否则可能无法正常工作! ");
//                System.exit(0);
            }
        }
    }



    void setSpeed(int downloadSpeed, int uploadSpeed) {
        config.setDownloadSpeed(downloadSpeed);
        config.setUploadSpeed(uploadSpeed);
        int s1 = (int) ((float) downloadSpeed * 1.1f);

        int s2 = (int) ((float) uploadSpeed * 1.1f);
        Route.localDownloadSpeed = downloadSpeed;
        Route.localUploadSpeed = config.uploadSpeed;

    }


    void exit() {
        System.exit(0);
    }




    ClientConfig loadConfig() {
        ClientConfig cfg = new ClientConfig();

        try {
            String content = readFileUtf8(configFilePath);
            JSONObject json = JSONObject.parseObject(content);
            cfg.setServerAddress(json.getString("server_address"));
            cfg.setServerPort(json.getIntValue("server_port"));
            cfg.setRemotePort(json.getIntValue("remote_port"));
            cfg.setRemoteAddress(json.getString("remote_address"));
            if (json.containsKey("direct_cn")) {
                cfg.setDirect_cn(json.getBooleanValue("direct_cn"));
            }
            cfg.setDownloadSpeed(json.getIntValue("download_speed"));
            cfg.setUploadSpeed(json.getIntValue("upload_speed"));
            if (json.containsKey("socks5_port")) {
                cfg.setSocks5Port(json.getIntValue("socks5_port"));
            }
            if (json.containsKey("protocal")) {
                cfg.setProtocal(json.getString("protocal"));
            }
            if (json.containsKey("auto_start")) {
                cfg.setAutoStart(json.getBooleanValue("auto_start"));
            }
            config = cfg;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cfg;
    }



    public static String readFileUtf8(String path) throws Exception {
        String str = null;
        FileInputStream fis = null;
        DataInputStream dis = null;
        try {
            File file = new File(path);

            int length = (int) file.length();
            byte[] data = new byte[length];

            fis = new FileInputStream(file);
            dis = new DataInputStream(fis);
            dis.readFully(data);
            str = new String(data, "utf-8");

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return str;
    }



    boolean haveNewVersion() {
        return serverVersion > localVersion;
    }

    public void checkUpdate() {
        for (int i = 0; i < 3; i++) {
            checkingUpdate = true;
            try {
                Properties propServer = new Properties();
                HttpURLConnection uc = Tools.getConnection(updateUrl);
                uc.setUseCaches(false);
                InputStream in = uc.getInputStream();
                propServer.load(in);
                serverVersion = Integer.parseInt(propServer.getProperty("version"));
                break;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(3 * 1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            } finally {
                checkingUpdate = false;
            }
        }


    }


    
	public static void setAutoRun(boolean run) {
		String s = new File(".").getAbsolutePath();
		String currentPaht = s.substring(0, s.length() - 1);
		StringBuffer sb = new StringBuffer();
		StringTokenizer st = new StringTokenizer(currentPaht, "\\");
		while (st.hasMoreTokens()) {
			sb.append(st.nextToken());
			sb.append("\\\\");
		}
		ArrayList<String> list = new ArrayList<String>();
		list.add("Windows Registry Editor Version 5.00");
		String name="fsclient";
//		if(PMClientUI.mc){
//			name="wlg_mc";
//		}
		if (run) {
			list.add("[HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run]");
			list.add("\""+name+"\"=\"" + sb.toString() + "finalspeedclient.exe -min" + "\"");
		} else {
			list.add("[HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run]");
			list.add("\""+name+"\"=-");
		}

		File file = null;
		try {
			file = new File("import.reg");
			FileWriter fw = new FileWriter(file);
			PrintWriter pw = new PrintWriter(fw);
			for (int i = 0; i < list.size(); i++) {
				String ss = list.get(i);
				if (!ss.equals("")) {
					pw.println(ss);
				}
			}
			pw.flush();
			pw.close();
			Process p = Runtime.getRuntime().exec("regedit /s " + "import.reg");
			p.waitFor();
		} catch (Exception e1) {
			// e1.printStackTrace();
		} finally {
			if (file != null) {
				file.delete();
			}
		}
	}




    @Override
    public boolean login() {
        return false;
    }


    @Override
    public boolean updateNode(boolean testSpeed) {
        return true;

    }

    public boolean isOsx_fw_pf() {
        return osx_fw_pf;
    }

    public void setOsx_fw_pf(boolean osx_fw_pf) {
        this.osx_fw_pf = osx_fw_pf;
    }

    public boolean isOsx_fw_ipfw() {
        return osx_fw_ipfw;
    }

    public void setOsx_fw_ipfw(boolean osx_fw_ipfw) {
        this.osx_fw_ipfw = osx_fw_ipfw;
    }

    public void setVisible(boolean visible) {
        this.isVisible = visible;
    }
}
