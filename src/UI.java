import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class UI extends JFrame implements ActionListener {

    // --- Clientクラスへの参照 (前回の回答で追加した部分) ---
    private Client userClient;
  
CardLayout cardLayout;
JPanel panel;
Color bgColor = new Color(200, 220, 255);
Client client;
boolean isVsCpu=true;


// main screen components
JPanel panel1;
JPanel panel1_mid;
JPanel panel1_bottom;
JLabel mainscreen_label_choice;
JButton mainscreen_button_player;
JButton mainscreen_button_cpu;
JButton mainscreen_button_end;

// cpu choice screen components
JPanel panel2;
JPanel panel2_2;
JPanel panel2_3;
JPanel panel2_4;
JLabel cpuchoicescreen_label_choice;
JButton cpuchoicescreen_button_gofirst;
JButton cpuchoicescreen_button_gosecond;
JRadioButton cpuchoicescreen_radiobutton_strong;
JRadioButton cpuchoicescreen_radiobutton_normal;
JRadioButton cpuchoicescreen_radiobutton_weak;
JLabel cpuchoicescreen_label_vscpu;
JButton cpuchoicescreen_button_confirm;

// login screen components
JPanel panel3;
JPanel panel3_loginpanel;
JPanel panel3_bottom;
JLabel loginscreen_label_insertnametitle;
JLabel loginscreen_label_insertname;
JTextArea loginscreen_textarea_name;
JButton loginscreen_button_ok;
JLabel loginscreen_label_vsplayer;

// game screen components
JPanel panel4;
JPanel panel4_player;
JPanel panel4_opp;
JPanel panel4_board;
JPanel panel4_top;
JPanel panel4_bottom;
JButton gamescreen_button_buttonarray[];
JLabel gamescreen_label_playername;
JLabel gamescreen_label_playerpiece;
JLabel gamescreen_label_playerpiececount;
JLabel gamescreen_label_oppname;
JLabel gamescreen_label_opppiece;
JLabel gamescreen_label_opppiececount;
JLabel gamescreen_label_turnplayer;
JButton gamescreen_button_surrender;


public UI(String title, Client userClient){
    super(title);
    this.userClient = userClient;
    System.out.println("test");
    cardLayout = new CardLayout();
    panel = new JPanel(cardLayout);

    // create screens
    mainScreen();
    cpuChoiceScreen();
    loginScreen();
    gameScreen();
    //panel1.setBackground(bgColor);
    //panel2.setBackground(bgColor);
    //panel3.setBackground(bgColor);
    //panel4.setBackground(bgColor);

    // add to card layout
    panel.add(panel1, "mainscreen");
    panel.add(panel2,"cpuchoicescreen");
    panel.add(panel3,"playerchoicescreen");
    panel.add(panel4,"gamescreen");

    cardLayout.show(panel,"mainscreen");

    // Add to frame
    add(panel);

  setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
  setSize(1000, 650);
  setVisible(true); 
}

public void actionPerformed(ActionEvent e) {
    String action=e.getActionCommand();
    switch(action){
        case "mainscreen_choseplayer":
            cardLayout.show(panel,"playerchoicescreen");
            break;
        case "mainscreen_chosecpu":
            cardLayout.show(panel,"cpuchoicescreen");
            break;
        case "cpuchoicescreen_choseconfirm":
            cardLayout.show(panel,"gamescreen");
            break;
        case "loginscreen_choseok":
            if(client.connectToServer(loginscreen_textarea_name.getText())){
                isVsCpu=false;
                gamescreen_button_surrender.setEnabled(false);
                cardLayout.show(panel,"gamescreen");   
            }else{
                cardLayout.show(panel,"playerchoicescreen");
            }
            break;   
        case "gamescreen_chosesurrender":
            cardLayout.show(panel,"mainscreen");
            break;
        case "cpuchoicescreen_chosefirst":
            cpuchoicescreen_button_gosecond.setBackground(null);
            cpuchoicescreen_button_gofirst.setBackground(Color.YELLOW);
            break;
        case "cpuchoicescreen_chosesecond":
            cpuchoicescreen_button_gofirst.setBackground(null);
            cpuchoicescreen_button_gosecond.setBackground(Color.YELLOW);
            break;
            
    }

    int pre_move=Integer.parseInt(action);
    int move[]={0,0};
    move[0]=pre_move/8;
    move[1]=pre_move%8;
    client.sendMoveToServer(null);
    
}

    public void setClient(Client userClient){
        this.userClient = userClient;
    }

// creating main screen
public void mainScreen(){

    // layout and stuffs

    panel1=new JPanel();
    panel1.setLayout(new BoxLayout(panel1, BoxLayout.Y_AXIS));
    panel1.setBorder(BorderFactory.createEmptyBorder(80, 100, 50, 100));

    panel1_mid=new JPanel();
    panel1_mid.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 0));
    panel1_bottom=new JPanel();
    panel1_bottom.setLayout(new FlowLayout());
    
    mainscreen_label_choice=new JLabel("プレイ方法を選択してください");
    mainscreen_label_choice.setFont(new Font("MS Gothic", Font.PLAIN, 30));
    mainscreen_label_choice.setAlignmentX(Component.CENTER_ALIGNMENT);
    mainscreen_button_player=new JButton("対人");
    mainscreen_button_player.setFont(new Font("MS Gothic", Font.PLAIN, 20));
    mainscreen_button_cpu=new JButton("対CPU");
    mainscreen_button_end=new JButton("終了");
    JButton[] buttons = {mainscreen_button_player, mainscreen_button_cpu, mainscreen_button_end};
    for (JButton b : buttons) {
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setPreferredSize(new Dimension(200, 100));
        b.setFont(new Font("MS Gothic", Font.BOLD, 30));
        b.setFocusPainted(false);
    }

    panel1.add(mainscreen_label_choice);
    panel1.add(Box.createRigidArea(new Dimension(0, 60)));
    panel1_mid.add(mainscreen_button_player);
    panel1_mid.add(mainscreen_button_cpu);
    panel1.add(panel1_mid);
    panel1.add(Box.createRigidArea(new Dimension(0, 40)));
    panel1_bottom.add(mainscreen_button_end);
    panel1.add(panel1_bottom);

    panel1_mid.setOpaque(false);
    panel1_bottom.setOpaque(false);

    // functions
    mainscreen_button_player.addActionListener(this);
    mainscreen_button_cpu.addActionListener(this);
    mainscreen_button_player.setActionCommand("mainscreen_choseplayer");
    mainscreen_button_cpu.setActionCommand("mainscreen_chosecpu");
    mainscreen_button_end.addActionListener(e -> System.exit(0));
}

// creating cpu choose screen
public void cpuChoiceScreen(){

    // layouts and stuffs

    panel2=new JPanel();
    panel2.setLayout(new BoxLayout(panel2, BoxLayout.Y_AXIS));
    panel2.setBorder(BorderFactory.createEmptyBorder(80, 70, 30, 70));

    panel2_2=new JPanel();
    panel2_2.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 0));
    panel2_3=new JPanel();
    panel2_3.setLayout(new FlowLayout(FlowLayout.CENTER, 60, 0));
    panel2_4=new JPanel();
    panel2_4.setLayout(new FlowLayout(FlowLayout.RIGHT));

    cpuchoicescreen_label_choice=new JLabel("先手後手を選択してください");
    cpuchoicescreen_label_choice.setFont(new Font("MS Gothic", Font.PLAIN, 30));
    cpuchoicescreen_label_choice.setAlignmentX(Component.CENTER_ALIGNMENT);
    cpuchoicescreen_button_gofirst=new JButton("先手先手");
    cpuchoicescreen_button_gosecond=new JButton("後手後手");
    cpuchoicescreen_radiobutton_strong=new JRadioButton("つよい",false);
    cpuchoicescreen_radiobutton_normal=new JRadioButton("ふつう",true);
    cpuchoicescreen_radiobutton_weak=new JRadioButton("よわい",false);
    cpuchoicescreen_label_vscpu=new JLabel("対CPU");
    cpuchoicescreen_label_vscpu.setAlignmentX(Component.CENTER_ALIGNMENT);
    cpuchoicescreen_label_vscpu.setFont(new Font("MS Gothic", Font.BOLD, 30));
    cpuchoicescreen_button_confirm=new JButton("決定");
    cpuchoicescreen_button_confirm.setFont(new Font("MS Gothic", Font.PLAIN, 25));
    JButton[] buttons = {cpuchoicescreen_button_gofirst, cpuchoicescreen_button_gosecond};
    for (JButton b : buttons) {
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setPreferredSize(new Dimension(200, 100));
        b.setFont(new Font("MS Gothic", Font.PLAIN, 30));
        b.setFocusPainted(false);
    }
    JRadioButton[] radioButtons = {cpuchoicescreen_radiobutton_strong, cpuchoicescreen_radiobutton_normal, cpuchoicescreen_radiobutton_weak};
    for (JRadioButton b : radioButtons) {
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setPreferredSize(new Dimension(100, 60));
        b.setFont(new Font("MS Gothic", Font.PLAIN, 25));
        b.setFocusPainted(false);
    }
    
    

    panel2.add(cpuchoicescreen_label_choice);
    panel2.add(Box.createRigidArea(new Dimension(0, 50)));
    panel2_2.add(cpuchoicescreen_button_gofirst);
    panel2_2.add(cpuchoicescreen_button_gosecond);
    panel2.add(panel2_2);
    panel2.add(Box.createRigidArea(new Dimension(0, 20)));
    panel2_3.add(cpuchoicescreen_radiobutton_strong);
    panel2_3.add(cpuchoicescreen_radiobutton_normal);
    panel2_3.add(cpuchoicescreen_radiobutton_weak);
    panel2.add(panel2_3);
    panel2.add(Box.createRigidArea(new Dimension(0, 50)));
    panel2.add(cpuchoicescreen_label_vscpu);
    panel2_4.add(cpuchoicescreen_button_confirm);
    panel2.add(panel2_4);

    //functions
    cpuchoicescreen_button_confirm.addActionListener(this);
    cpuchoicescreen_button_confirm.setActionCommand("cpuchoicescreen_choseconfirm");
    cpuchoicescreen_button_gofirst.addActionListener(this);
    cpuchoicescreen_button_gofirst.setActionCommand("cpuchoicescreen_chosefirst");
    cpuchoicescreen_button_gosecond.addActionListener(this);
    cpuchoicescreen_button_gosecond.setActionCommand("cpuchoicescreen_chosesecond");
    ButtonGroup group=new ButtonGroup();
    group.add(cpuchoicescreen_radiobutton_strong);
    group.add(cpuchoicescreen_radiobutton_normal);
    group.add(cpuchoicescreen_radiobutton_weak);

}


  // creating login screen
public void loginScreen(){

    // layouts and stuffs

    panel3=new JPanel();
    panel3.setLayout(new BoxLayout(panel3, BoxLayout.Y_AXIS));
    panel3.setBorder(BorderFactory.createEmptyBorder(100, 250, 50, 250));

    panel3_loginpanel=new JPanel();
    panel3_loginpanel.setLayout(new BoxLayout(panel3_loginpanel, BoxLayout.Y_AXIS));
    panel3_loginpanel.setBorder(BorderFactory.createTitledBorder("名前の入力"));

    panel3_bottom=new JPanel();

    loginscreen_label_insertname=new JLabel("名前を入力してください");
    loginscreen_label_insertname.setAlignmentX(Component.CENTER_ALIGNMENT);
    loginscreen_label_insertname.setFont(new Font("MS Gothic", Font.BOLD, 18));
    loginscreen_textarea_name=new JTextArea(1,20);
    loginscreen_textarea_name.setAlignmentX(Component.CENTER_ALIGNMENT);
    loginscreen_button_ok=new JButton("OK");
    loginscreen_button_ok.setAlignmentX(Component.CENTER_ALIGNMENT);

    loginscreen_textarea_name.setMaximumSize(new Dimension(200, 30));
    loginscreen_button_ok.setMaximumSize(new Dimension(80, 30));

    panel3_loginpanel.add(Box.createVerticalStrut(10));
    panel3_loginpanel.add(loginscreen_label_insertname);
    panel3_loginpanel.add(Box.createVerticalStrut(10));
    panel3_loginpanel.add(loginscreen_textarea_name);
    panel3_loginpanel.add(Box.createVerticalStrut(10));
    panel3_loginpanel.add(loginscreen_button_ok);


    loginscreen_label_vsplayer=new JLabel("対人");
    loginscreen_label_vsplayer.setFont(new Font("MS Gothic", Font.PLAIN, 30));
    panel3_bottom.add(loginscreen_label_vsplayer);

    panel3.add(Box.createVerticalGlue());
    panel3.add(panel3_loginpanel);
    panel3.add(Box.createVerticalStrut(20));
    panel3.add(panel3_bottom);
    panel3.add(Box.createVerticalGlue());

    // functions
    loginscreen_button_ok.addActionListener(this);
    loginscreen_button_ok.setActionCommand("loginscreen_choseok");
}
  // game screen
  public void gameScreen(){

    // layouts and stuffs

    ImageIcon blackIcon, whiteIcon, boardIcon;
    whiteIcon = new ImageIcon("White.jpg");
    blackIcon = new ImageIcon("Black.jpg");
    boardIcon = new ImageIcon("GreenFrame.jpg");

    panel4=new JPanel();
    panel4.setLayout(new BorderLayout(10, 10));
    panel4.setBorder(BorderFactory.createEmptyBorder(10, 35, 0, 35));

    panel4_player=new JPanel();
    panel4_player.setLayout(new BoxLayout(panel4_player, BoxLayout.Y_AXIS));
    panel4_player.setBorder(BorderFactory.createEmptyBorder(10, 0, 30, 0));
    JPanel panel4_player_offset=new JPanel(new BorderLayout());
    panel4_player_offset.add(panel4_player,BorderLayout.NORTH);
    panel4_player_offset.setPreferredSize(new Dimension(150, 600));

    panel4_opp=new JPanel();
    panel4_opp.setLayout(new BoxLayout(panel4_opp, BoxLayout.Y_AXIS));
    panel4_opp.setBorder(BorderFactory.createEmptyBorder(10, 0, 30, 0));
    JPanel panel4_opp_offset=new JPanel(new BorderLayout());
    panel4_opp_offset.add(panel4_opp,BorderLayout.NORTH);
    panel4_opp_offset.setPreferredSize(new Dimension(150, 600));

    panel4_board=new JPanel();
    panel4_board.setLayout(new FlowLayout());
    JPanel panel4_board_offset=new JPanel();
    panel4_board_offset.setLayout(new GridLayout(8,8));
    panel4_board_offset.setPreferredSize(new Dimension(480, 480));
    gamescreen_button_buttonarray = new JButton[64];
    for (int i = 0; i < 64; i++) {
        gamescreen_button_buttonarray[i] = new JButton();
        gamescreen_button_buttonarray[i].setPreferredSize(new Dimension(60, 60));
        panel4_board_offset.add(gamescreen_button_buttonarray[i]); 
        gamescreen_button_buttonarray[i].addActionListener(this);
        gamescreen_button_buttonarray[i].setActionCommand(Integer.toString(i));// y=i/8, x=i%8
        
    }
    panel4_board.add(panel4_board_offset);

    panel4_bottom=new JPanel();
    panel4_bottom.setLayout(new FlowLayout(FlowLayout.RIGHT,50,0));
    gamescreen_button_surrender=new JButton("退出");
    gamescreen_button_surrender.setPreferredSize(new Dimension(80,80));
    gamescreen_button_surrender.setFont(new Font("MS Gothic", Font.BOLD, 20));
    gamescreen_label_turnplayer=new JLabel("dsadsadの番");
    gamescreen_label_turnplayer.setAlignmentX(Component.CENTER_ALIGNMENT);
    gamescreen_label_turnplayer.setFont(new Font("MS Gothic", Font.PLAIN, 30));
    panel4_bottom.add(gamescreen_label_turnplayer);
    panel4_bottom.add(Box.createRigidArea(new Dimension(150, 0)));
    panel4_bottom.add(gamescreen_button_surrender);
    panel4_bottom.setPreferredSize(new Dimension(1000,100));

    gamescreen_label_playername=new JLabel("Player 1");
    gamescreen_label_playername.setFont(new Font("Arial", Font.PLAIN, 30));
    gamescreen_label_playername.setAlignmentX(Component.CENTER_ALIGNMENT);
    gamescreen_label_playerpiece=new JLabel("黒");
    gamescreen_label_playerpiece.setFont(new Font("MS Gothic", Font.PLAIN, 30));
    gamescreen_label_playerpiece.setAlignmentX(Component.CENTER_ALIGNMENT);
    gamescreen_label_playerpiececount=new JLabel(("piece: 0"));
    gamescreen_label_playerpiececount.setFont(new Font("Arial", Font.PLAIN, 30));
    gamescreen_label_playerpiececount.setAlignmentX(Component.CENTER_ALIGNMENT);

    gamescreen_label_oppname=new JLabel("Player 1");
    gamescreen_label_oppname.setFont(new Font("Arial", Font.PLAIN, 30));
    gamescreen_label_oppname.setAlignmentX(Component.CENTER_ALIGNMENT);
    gamescreen_label_opppiece=new JLabel("黒");
    gamescreen_label_opppiece.setFont(new Font("MS Gothic", Font.PLAIN, 30));
    gamescreen_label_opppiece.setAlignmentX(Component.CENTER_ALIGNMENT);
    gamescreen_label_opppiececount=new JLabel(("piece: 0"));
    gamescreen_label_opppiececount.setFont(new Font("Arial", Font.PLAIN, 30));
    gamescreen_label_opppiececount.setAlignmentX(Component.CENTER_ALIGNMENT);

    panel4_player.add(gamescreen_label_playername);
    panel4_player.add(Box.createRigidArea(new Dimension(0, 50)));
    panel4_player.add(gamescreen_label_playerpiece);
    panel4_player.add(Box.createRigidArea(new Dimension(0, 50)));
    panel4_player.add(gamescreen_label_playerpiececount);

    panel4_opp.add(gamescreen_label_oppname);
    panel4_opp.add(Box.createRigidArea(new Dimension(0, 50)));
    panel4_opp.add(gamescreen_label_opppiece);
    panel4_opp.add(Box.createRigidArea(new Dimension(0, 50)));
    panel4_opp.add(gamescreen_label_opppiececount);

    panel4.add(panel4_player_offset, BorderLayout.WEST);
    panel4.add(panel4_board,BorderLayout.CENTER);
    panel4.add(panel4_opp_offset,BorderLayout.EAST);
    panel4.add(panel4_bottom,BorderLayout.SOUTH);

    // functions
    gamescreen_button_surrender.addActionListener(this);
    gamescreen_button_surrender.setActionCommand("gamescreen_chosesurrender");

  }

  // dialogue pop up 

  // to update player info
  public void updatePlayerInfo(String playerName,String playerColor){
    gamescreen_label_playername.setText(playerName);
   // gamescreen_label_p

  }

  // to update opp info
  public void updateOpponentInfo(String oppName,String oppColor){

  }

  // to update turn player
  public void updateTurnLabel(String turnPlayer){

  }

  // to disable/enable board
  public void enableBoardInput(boolean state){

  }

}

