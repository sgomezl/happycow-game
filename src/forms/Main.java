package forms;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Main {
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final int PLAYER_SIZE = 96;

    private JPanel panelMain;
    private JPanel panelCenter;
    private JPanel panelGame;
    private JLabel labelFarm;

    private Timer timer;
    private Timer wellnessTimer;

    private int cowX = 1140, cowY = 260;
    private final int SPEED = 5;
    private boolean up, down, left, right;

    private String direction = "DOWN";
    private int stepCounter = 0;
    private boolean stepToggle = false;

    private BufferedImage imgUp1, imgUp2;
    private BufferedImage imgDown1, imgDown2;
    private BufferedImage imgLeft1, imgLeft2;
    private BufferedImage imgRight1, imgRight2;

    private boolean foodVisible = false;
    private boolean crowVisible = false;

    private Point foodPosition;
    private Point crowPosition;

    private final int ITEM_WIDTH = 60;
    private final int ITEM_HEIGHT = 60;
    private final BufferedImage foodImage;
    private final BufferedImage crowImage;

    private int energy = 0;
    private int wellness = 100;
    private String playerName;

    private void showIntroDialog() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JTextArea instructions = new JTextArea(
                "OBJETIVO:\nRecoge comida y llena el medidor de energía al 100\n" +
                        "antes de que se agote el confort.\n\n" +
                        "¡Cuidado! Los cuervos hacen que el confort disminuya más rápido.\n" +
                        "Atrápalos para evitar que te perjudiquen.\n" +
                        "\n¡Tu puntuación final será el confort restante al acabar la partida!");
        instructions.setEditable(false);
        instructions.setFont(new Font("Arial", Font.PLAIN, 14));
        instructions.setBackground(null);
        instructions.setBorder(null);

        JTextField nameField = new JTextField(15);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(new JLabel("Introduce tu nombre:"), BorderLayout.NORTH);
        inputPanel.add(nameField, BorderLayout.CENTER);

        panel.add(instructions, BorderLayout.NORTH);
        panel.add(inputPanel, BorderLayout.SOUTH);

        int result;
        do {
            result = JOptionPane.showConfirmDialog(null, panel, "Bienvenido a HappyCow",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            playerName = nameField.getText().trim();
        } while (result == JOptionPane.OK_OPTION && playerName.isEmpty());

        if (result != JOptionPane.OK_OPTION) {
            System.exit(0);  //Cierra si se cancela
        }
    }

    public Main() {
        showIntroDialog();
        panelMain = new JPanel(null);
        panelMain.setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        panelMain.setFocusable(true);

        showPanelCenter();

        panelGame = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                BufferedImage currentImg = switch (direction) {
                    case "UP" -> stepToggle ? imgUp1 : imgUp2;
                    case "DOWN" -> stepToggle ? imgDown1 : imgDown2;
                    case "LEFT" -> stepToggle ? imgLeft1 : imgLeft2;
                    case "RIGHT" -> stepToggle ? imgRight1 : imgRight2;
                    default -> null;
                };

                g.drawImage(currentImg, cowX, cowY, PLAYER_SIZE, PLAYER_SIZE, null);

                if (!foodVisible) spawnItem("food");
                if (foodVisible && foodPosition != null) {
                    g.drawImage(foodImage, foodPosition.x, foodPosition.y, ITEM_WIDTH, ITEM_HEIGHT, null);
                }

                if (crowVisible && crowPosition != null) {
                    g.drawImage(crowImage, crowPosition.x, crowPosition.y, ITEM_WIDTH, ITEM_HEIGHT, null);
                }

                g.setColor(Color.BLACK);
                g.setFont(new Font("Arial", Font.BOLD, 20));
                g.drawString("Energía: " + energy, 30, 45);
                g.drawString("Comfort: " + wellness, 30, 80);
            }
        };
        panelGame.setBounds(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        panelGame.setOpaque(false);
        panelGame.setFocusable(true);

        panelMain.add(panelCenter);
        panelMain.add(panelGame);

        panelMain.setComponentZOrder(panelGame, 0);
        panelMain.setComponentZOrder(panelCenter, 1);

        loadImages();
        foodImage = toBufferedImage(new ImageIcon("src/images/food.png").getImage());
        crowImage = toBufferedImage(new ImageIcon("src/images/crow.png").getImage());
        spawnItem("food");

        timer = new Timer(10, e -> {
            move();
            checkCollisions();
            panelGame.repaint();
            if (up || down || left || right) {
                stepCounter++;
                if (stepCounter >= 5) {
                    stepToggle = !stepToggle;
                    stepCounter = 0;
                }
            } else stepCounter = 0;
            if (energy > 0 && (energy % 25 == 0) && !crowVisible) spawnItem("crow");

        });
        timer.start();

        wellnessTimer = new Timer(1000, e -> {
            if (wellness > 0) {
                wellness -= crowVisible ? 3 : 1;
                if (wellness < 0) wellness = 0;
            }
            if (wellness == 0) {
                timer.stop();
                wellnessTimer.stop();
                JOptionPane.showMessageDialog(null, "Game Over: Tu vaca ha perdido el confort.");
            }
        });
        wellnessTimer.start();

        KeyAdapter keyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP -> up = true;
                    case KeyEvent.VK_DOWN -> down = true;
                    case KeyEvent.VK_LEFT -> left = true;
                    case KeyEvent.VK_RIGHT -> right = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP -> up = false;
                    case KeyEvent.VK_DOWN -> down = false;
                    case KeyEvent.VK_LEFT -> left = false;
                    case KeyEvent.VK_RIGHT -> right = false;
                }
            }
        };
        panelMain.addKeyListener(keyAdapter);
    }

    private void showPanelCenter() {
        panelCenter = new JPanel(null);
        panelCenter.setBounds(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        showLabelFarm();
    }

    private void showLabelFarm() {
        labelFarm = new JLabel();
        labelFarm.setBounds(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        ImageIcon imageIcon = new ImageIcon("src/images/farm_all_closed.png");
        Icon icon = new ImageIcon(imageIcon.getImage().getScaledInstance(labelFarm.getWidth(), labelFarm.getHeight(), Image.SCALE_SMOOTH));
        labelFarm.setIcon(icon);
        panelCenter.add(labelFarm);
    }

    private BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage buffimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D bGr = buffimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();
        return buffimage;
    }

    private void loadImages() {
        imgUp1 = toBufferedImage(new ImageIcon("src/images/cow_up1.png").getImage());
        imgUp2 = toBufferedImage(new ImageIcon("src/images/cow_up2.png").getImage());
        imgDown1 = toBufferedImage(new ImageIcon("src/images/cow_down1.png").getImage());
        imgDown2 = toBufferedImage(new ImageIcon("src/images/cow_down2.png").getImage());
        imgLeft1 = toBufferedImage(new ImageIcon("src/images/cow_left1.png").getImage());
        imgLeft2 = toBufferedImage(new ImageIcon("src/images/cow_left2.png").getImage());
        imgRight1 = toBufferedImage(new ImageIcon("src/images/cow_right1.png").getImage());
        imgRight2 = toBufferedImage(new ImageIcon("src/images/cow_right2.png").getImage());
    }

    private void move() {
        double dx = 0, dy = 0;
        if (up) {
            dy -= SPEED;
            direction = "UP";
        }
        if (down) {
            dy += SPEED;
            direction = "DOWN";
        }
        if (left) {
            dx -= SPEED;
            direction = "LEFT";
        }
        if (right) {
            dx += SPEED;
            direction = "RIGHT";
        }

        //esto hace que la velocidad diagonal no sea superior
        if (dx != 0 && dy != 0) {
            dx /= Math.sqrt(2);
            dy /= Math.sqrt(2);
        }

        int newX = cowX + (int) dx;
        int newY = cowY + (int) dy;

        if (newX < 0) newX = 0;
        if (newX > WINDOW_WIDTH - PLAYER_SIZE) newX = WINDOW_WIDTH - PLAYER_SIZE;
        if (newY < 0) newY = 0;
        if (newY > WINDOW_HEIGHT - PLAYER_SIZE) newY = WINDOW_HEIGHT - PLAYER_SIZE;

        //colisión diagonal agua
        double m = 170.0 / 350.0;
        double b = 550.0;
        double limiteY = m * newX + b;
        if (newX <= 350 && newY + PLAYER_SIZE > limiteY) newY = (int) (limiteY - PLAYER_SIZE);

        Point pos = resolverColision(newX, newY, 0, 0, 265, 170);
        newX = pos.x;
        newY = pos.y;

        pos = resolverColision(newX, newY, 1080, 0, 200, 170);
        newX = pos.x;
        newY = pos.y;

        cowX = newX;
        cowY = newY;
    }

    private Point resolverColision(int px, int py, int ox, int oy, int oWidth, int oHeight) {
        int playerRight = px + PLAYER_SIZE;
        int playerBottom = py + PLAYER_SIZE;
        int objectRight = ox + oWidth;
        int objectBottom = oy + oHeight;

        boolean overlapX = playerRight > ox && px < objectRight;
        boolean overlapY = playerBottom > oy && py < objectBottom;

        if (overlapX && overlapY) {
            int overlapRight = playerRight - ox;
            int overlapLeft = objectRight - px;
            int overlapDown = playerBottom - oy;
            int overlapUp = objectBottom - py;

            int minOverlapX = Math.min(overlapRight, overlapLeft);
            int minOverlapY = Math.min(overlapDown, overlapUp);

            if (minOverlapX < minOverlapY) px += (overlapRight < overlapLeft) ? -overlapRight : overlapLeft;
            else py += (overlapDown < overlapUp) ? -overlapDown : overlapUp;
        }
        return new Point(px, py);
    }

    private void spawnItem(String type) {
        int maxX = WINDOW_WIDTH - ITEM_WIDTH;
        int maxY = WINDOW_HEIGHT - ITEM_HEIGHT;

        while (true) {
            int fx = (int) (Math.random() * (maxX + 1));
            int fy = (int) (Math.random() * (maxY + 1));

            if (fx <= 350) {
                double limiteY = (170.0 / 350.0) * fx + 550.0;
                if (fy + ITEM_HEIGHT > limiteY) continue;
            }

            if (fx + ITEM_WIDTH > 0 && fx < 265 && fy + ITEM_HEIGHT > 0 && fy < 170) continue;
            if (fx + ITEM_WIDTH > 1080 && fx < 1280 && fy + ITEM_HEIGHT > 0 && fy < 170) continue;

            Point pos = new Point(fx, fy);
            switch (type) {
                case "food" -> {
                    foodPosition = pos;
                    foodVisible = true;
                }
                case "crow" -> {
                    crowPosition = pos;
                    crowVisible = true;
                }
            }
            break;
        }
    }

    private void checkCollisions() {
        if (foodVisible && foodPosition != null && collides(foodPosition)) {
            foodVisible = false;
            foodPosition = null;
            energy += 5;

            if (energy >= 100) {
                timer.stop();
                wellnessTimer.stop();
                saveScore(playerName, wellness);

                int option = JOptionPane.showConfirmDialog(null,
                        "¡Felicidades " + playerName + "! Tu vaca está llena de energía.\n" +
                                "Confort restante: " + wellness + "\n¿Quieres volver a jugar?",
                        "Victoria", JOptionPane.YES_NO_OPTION);

                if (option == JOptionPane.YES_OPTION) {
                    restartGame();
                } else {
                    System.exit(0);
                }
            }
        }

        if (crowVisible && crowPosition != null && collides(crowPosition)) {
            crowVisible = false;
            crowPosition = null;
        }
    }

    private boolean collides(Point itemPos) {
        return cowX + PLAYER_SIZE > itemPos.x && cowX < itemPos.x + ITEM_WIDTH &&
                cowY + PLAYER_SIZE > itemPos.y && cowY < itemPos.y + ITEM_HEIGHT;
    }

    private void restartGame() {
        cowX = 1140;
        cowY = 260;
        energy = 0;
        wellness = 100;
        foodVisible = false;
        crowVisible = false;
        spawnItem("food");
        timer.start();
        wellnessTimer.start();
    }

    private void saveScore(String name, int wellnessLeft) {
        String sql = "INSERT INTO scores(player_name, comfort_remaining) VALUES(?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, wellnessLeft);
            pstmt.executeUpdate();
            System.out.println("Dades guardades correctament a MySQL.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public static Connection connect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://localhost:3306/happycow_db";
            String user = "root";
            String password = "mysql";
            return DriverManager.getConnection(url, user, password);
        } catch (ClassNotFoundException e) {
            System.out.println("Driver MySQL no trobat.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Error de connexió: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("HappyCow");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(new Main().panelMain);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.setLayout(null);

        Toolkit pantalla = Toolkit.getDefaultToolkit();
        Image icono = pantalla.getImage("src/images/cow.png");
        frame.setIconImage(icono);
    }
}
