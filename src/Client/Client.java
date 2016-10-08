package Client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;


public class Client extends Thread implements ActionListener {
    //是否停止
    public static int STOP = 0;

    //在线用户列表
    public static Map<String, SocketAddress> userMap = new HashMap();
    private static String username = "Froid";

    private DatagramSocket client;

    private JFrame frame;
    //聊天信息
    private JTextArea info;
    //在线用户

    private JTextArea onlineUser;
    private JTextArea msgText;
    private JButton sendButton;

    public Client(DatagramSocket client) throws Exception {

        this.client = client;
        this.frame = new JFrame();
        frame.setSize(400, 400);
        frame.setTitle("P2P Chatting System.");
        frame.setFont(new Font("Helvetica", Font.PLAIN, 14));
        frame.setLayout(new GridLayout(0, 1, 5, 5));

        sendButton = new JButton("Send.");
        JScrollBar scroll = new JScrollBar();

        //信息窗口
        this.info = new JTextArea(25, 25);
        //激活自动换行功能
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setEditable(false);
        scroll.add(info);
        JPanel infopanel = new JPanel();
        JLabel labelInfo = new JLabel("Info Window.");
        infopanel.add(info, BorderLayout.SOUTH);
        infopanel.add(labelInfo, BorderLayout.NORTH);


        //用户窗口
        JLabel labelUser = new JLabel("Online User.");
        JPanel userpanel = new JPanel();
        onlineUser = new JTextArea(25, 25);
        onlineUser.setLineWrap(true);
        onlineUser.setWrapStyleWord(true);
        onlineUser.setEditable(false);

        userpanel.add(onlineUser, BorderLayout.CENTER);
        userpanel.add(labelUser, BorderLayout.BEFORE_FIRST_LINE);


        JPanel msgpanel = new JPanel();
        msgText = new JTextArea(5, 25);
        msgText.setLineWrap(true);
        // msgText.setBounds(10,10,50,100);
        msgpanel.add(msgText, BorderLayout.CENTER);
        msgpanel.add(sendButton, BorderLayout.BEFORE_FIRST_LINE);
        frame.getContentPane().add(userpanel);
        frame.getContentPane().add(infopanel);
        frame.getContentPane().add(msgpanel);


        frame.pack();
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
        sendButton.addActionListener(this);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });


    }

    public static void main(String args[]) throws Exception {

        String serverIP = "192.168.1.5";///122.225.99.40
        int port = 6636;

        //构造一个目标地址
        SocketAddress target = new InetSocketAddress(serverIP, port);

        DatagramSocket client = new DatagramSocket();
        String msg = Client.username + "#" + " logins the server.";
        byte[] buf = msg.getBytes();
        //向服务器发送上线数据
        DatagramPacket packet = new DatagramPacket(buf, buf.length, target);
        client.send(packet);
        new Client(client).start();

    }

    /**
     * 给其他在线用户发送心跳 保持session有效
     */
    private void sendSkip() {
        new Thread() {
            public void run() {
                try {
                    String msg = "skip";
                    while (true) {
                        if (STOP == 1)
                            break;
                        if (userMap.size() > 0) {
                            for (Map.Entry<String, SocketAddress> entry : userMap.entrySet()) {
                                DatagramPacket data = new DatagramPacket(msg.getBytes(), msg.getBytes().length, entry.getValue());
                                client.send(data);
                            }
                        }
                        //每10s发送一次心跳
                        Thread.sleep(30 * 1000);
                    }
                } catch (Exception e) {
                }

            }
        }.start();
    }

    //主要任务是接收数据
    //可以是其他用户发来的信息，也可以是服务器发来的在线用户数据
    public void run() {
        try {

            String msg;
            DatagramPacket data;

            //执行心跳
            sendSkip();

            while (true) {
                if (STOP == 1)
                    break;
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                client.receive(packet);
                msg = new String(packet.getData(), 0, packet.getLength());
                if (msg.length() > 0) {
                    if (msg.indexOf("server:") > -1) {
                        //服务器数据 格式server:ID#name:IP:PORT,  .....
                        String userdata = msg.substring(msg.indexOf(":") + 1, msg.length());
                        String[] user = userdata.split(",");
                        for (String u : user) {
                            if (u != null && u.length() > 0) {
                                String[] udata = u.split("#");
                                String ip = udata[1].split(":")[1];
                                String name = udata[1].split(":")[0];
                                int port = Integer.parseInt(udata[1].split(":")[2]);

                                ip = ip.substring(1, ip.length());

                                SocketAddress adds = new InetSocketAddress(ip, port);
                                userMap.put(name, adds);
                                //给对方打洞 发送空白报文
                                data = new DatagramPacket(new byte[0], 0, adds);
                                client.send(data);

                            }

                        }
                        //更新在线用户列表
                        this.onlineUser.setText("");
                        for (Map.Entry<String, SocketAddress> entry : userMap.entrySet()) {
                            this.onlineUser.append("User " + entry.getKey() + " from \"" + entry.getValue().toString().substring(1) + "\"\n");
                        }

                    } else if (msg.indexOf("skip") > -1) ;
                    else {
                        //普通消息
                        this.info.append(msg);
                        //this.info.append(packet.getAddress().toString().substring(1)+":"+packet.getPort()+":"+msg);
                        this.info.append("\n");
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    //按钮事件
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == this.sendButton) {
            try {
                String msg = Client.username + ":" + this.msgText.getText();
                if (msg.length() > 0) {
                    this.info.append(msg);
                    this.info.append("\n");
                    for (Map.Entry<String, SocketAddress> entry : userMap.entrySet()) {
                        DatagramPacket data = new DatagramPacket(msg.getBytes(), msg.getBytes().length, entry.getValue());
                        client.send(data);
                    }

                    this.msgText.setText("");
                }
            } catch (Exception ee) {
            }
        }

    }
}



