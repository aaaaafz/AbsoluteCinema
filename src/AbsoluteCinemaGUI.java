import com.google.gson.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.AttributedString;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import model.Movie;

public class AbsoluteCinemaGUI extends JFrame {

    // ---------------------------------------------------------
    // PALETTE + FONTS
    // ---------------------------------------------------------

    private final Color background = new Color(10, 14, 18);
    private final Color sidebarColor = background;
    private final Color cardColor = new Color(20, 28, 34);
    private final Color elevatedColor = new Color(27, 37, 44);
    private final Color green = new Color(66, 205, 160);
    private final Color textColor = new Color(240, 243, 245);
    private final Color muted = new Color(130, 141, 150);
    private final Color iconIdle = new Color(140, 152, 162);
    private final Color navText = new Color(182, 190, 198);
    private final Color borderColor = new Color(38, 50, 58);
    private final Color activeNav = new Color(22, 34, 39);
    private final Color transparent = new Color(0, 0, 0, 0);
    private final Color darkText = new Color(10, 14, 18);

    private final String sansFamily = pickFont(
            "Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Arial", "SansSerif");
    private final String serifFamily = pickFont(
            "Playfair Display", "Georgia", "Times New Roman", "Serif");

    private final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

    private static final int SIDEBAR_FULL = 216;
    private static final int SIDEBAR_RAIL = 74;

    // ---------------------------------------------------------
    // STATE
    // ---------------------------------------------------------

    private final JPanel content = new JPanel(new BorderLayout(0, 0));
    private final JPanel pagePanel = new JPanel(new BorderLayout(0, 16));
    private final JLabel statusLabel = new JLabel(" ");

    private final JTextField searchField = new JTextField() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(muted);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                Insets in = getInsets();
                g2.drawString("Search movies by title...", in.left,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    };

    private final ArrayList<Movie> movies = new ArrayList<>();
    private final Gson gson = new Gson();
    private final HttpClient imageClient = HttpClient.newHttpClient();

    private final Map<String, ImageIcon> posterCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> rawCache = new ConcurrentHashMap<>();

    private final String imageBase = "https://image.tmdb.org/t/p/w342";

    private String currentPage = "Home";
    private int navToken = 0;
    private Runnable currentSavedView = null;
    private HeroBanner activeHero = null;

    private final Map<String, JButton> navButtons = new LinkedHashMap<>();
    private final Map<String, IconKind> navKinds = new LinkedHashMap<>();
    private final Map<String, Runnable> navActions = new LinkedHashMap<>();
    private final Map<String, TopTab> topTabs = new LinkedHashMap<>();

    // sidebar pieces
    private JPanel sidebar;
    private JLabel logoMark;
    private LogoLabel logoText;
    private JLabel libraryLabel;
    private JPanel footerPanel;
    private int sidebarWidth = SIDEBAR_FULL;
    private boolean sidebarExpanded = true;
    private javax.swing.Timer sidebarTimer;

    public AbsoluteCinemaGUI() {
        setTitle("AbsoluteCinema");
        setSize(1280, 860);
        setMinimumSize(new Dimension(1000, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(background);
        setLayout(new BorderLayout());

        try {
            ArrayList<Movie> loaded = MovieStorage.loadMovies();
            if (loaded != null) {
                movies.addAll(loaded);
            }
        } catch (Exception e) {
            showError("Could not load your saved movies.", e);
        }

        add(createSidebar(), BorderLayout.WEST);

        content.setBackground(background);
        content.setBorder(new EmptyBorder(14, 12, 8, 24));
        content.add(createTopBar(), BorderLayout.NORTH);

        pagePanel.setBackground(background);
        content.add(pagePanel, BorderLayout.CENTER);

        statusLabel.setForeground(new Color(95, 106, 115));
        statusLabel.setFont(sans(Font.PLAIN, 10));
        statusLabel.setBorder(new EmptyBorder(6, 0, 0, 0));
        content.add(statusLabel, BorderLayout.SOUTH);

        add(content, BorderLayout.CENTER);

        showDiscoverPage();
        setActiveNavigation("Home");
    }

    // ---------------------------------------------------------
    // FONT HELPERS
    // ---------------------------------------------------------

    private static String pickFont(String... names) {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames()));
        for (String name : names) {
            if (available.contains(name)) {
                return name;
            }
        }
        return names[names.length - 1];
    }

    private Font sans(int style, float size) {
        return new Font(sansFamily, Font.PLAIN, 12).deriveFont(style, size);
    }

    private Font serif(int style, float size) {
        return new Font(serifFamily, Font.PLAIN, 12).deriveFont(style, size);
    }

    private static Font spaced(Font font, float tracking) {
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.TRACKING, tracking);
        return font.deriveFont(attrs);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static void invalidateDeep(Component c) {
        c.invalidate();
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                invalidateDeep(child);
            }
        }
    }

    // ---------------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------------

    private void navigateTo(String label) {
        Runnable action = navActions.get(label);
        if (action != null) {
            action.run();
        }
        setActiveNavigation(label);
    }

    private void setActiveNavigation(String label) {
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            JButton button = entry.getValue();
            boolean active = entry.getKey().equals(label);
            IconKind kind = navKinds.get(entry.getKey());

            button.setBackground(active ? activeNav : transparent);
            button.setForeground(active ? textColor : navText);
            button.setIcon(new VectorIcon(kind, 18,
                    active ? green : iconIdle,
                    kind == IconKind.HOME || kind == IconKind.GRID
                            || kind == IconKind.HEART));
            button.repaint();
        }

        String topActive = "Watchlist".equals(label) ? "Watchlist" : "Discover";
        for (Map.Entry<String, TopTab> entry : topTabs.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(topActive));
        }
    }

    // ---------------------------------------------------------
    // SIDEBAR
    // ---------------------------------------------------------

    private JPanel createSidebar() {
        sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(sidebarWidth, 0));
        sidebar.setBackground(sidebarColor);
        sidebar.setBorder(new EmptyBorder(14, 15, 22, 15));

        JPanel logoRow = new JPanel(new BorderLayout(10, 0));
        logoRow.setOpaque(false);
        logoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoRow.setPreferredSize(new Dimension(180, 44));
        logoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        logoMark = new JLabel(new VectorIcon(IconKind.FILM, 30, green, true));
        logoText = new LogoLabel();
        logoRow.add(logoMark, BorderLayout.WEST);
        logoRow.add(logoText, BorderLayout.CENTER);

        sidebar.add(logoRow);
        sidebar.add(Box.createVerticalStrut(22));

        addNavButton(sidebar, "Home", IconKind.HOME, this::showDiscoverPage);
        addNavButton(sidebar, "Discover", IconKind.COMPASS,
                () -> loadCategoryPage("Discover", TmdbClient::getTrendingMovies));
        addNavButton(sidebar, "Popular", IconKind.FLAME, this::showPopularPage);
        addNavButton(sidebar, "Top Rated", IconKind.STAR, this::showTopRatedPage);
        addNavButton(sidebar, "New Releases", IconKind.CALENDAR, this::showNowPlayingPage);
        addNavButton(sidebar, "Genres", IconKind.GRID, this::showGenresPage);

        sidebar.add(Box.createVerticalStrut(14));
        JPanel divider = new JPanel();
        divider.setBackground(borderColor);
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        divider.setPreferredSize(new Dimension(100, 1));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sidebar.add(divider);
        sidebar.add(Box.createVerticalStrut(20));

        libraryLabel = new JLabel("YOUR LIBRARY");
        libraryLabel.setForeground(muted);
        libraryLabel.setFont(spaced(sans(Font.PLAIN, 10), 0.14f));
        libraryLabel.setBorder(new EmptyBorder(0, 12, 0, 6));
        libraryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(libraryLabel);
        sidebar.add(Box.createVerticalStrut(12));

       
addNavButton(sidebar, "Watchlist", IconKind.BOOKMARK,
        () -> showSavedPage("Watchlist",
                "Films you plan to watch.", m -> !m.isWatched()));

addNavButton(sidebar, "Watched", IconKind.CHECK,
        () -> showSavedPage("Watched",
                "Films you have watched.", Movie::isWatched));

addNavButton(sidebar, "Reviews", IconKind.CHAT,
        () -> showSavedPage("Reviews",
                "Films you have rated.", m -> m.getRating() > 0));


        sidebar.add(Box.createVerticalGlue());

        footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setOpaque(false);
        footerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerPanel.setBorder(new EmptyBorder(0, 12, 0, 0));

        // JLabel line1 = new JLabel("Better movies.");
        // JLabel line2 = new JLabel("Bigger conversations.");
        // for (JLabel l : new JLabel[]{line1, line2}) {
        //     l.setForeground(new Color(168, 177, 185));
        //     l.setFont(spaced(sans(Font.ITALIC, 12), 0.012f));
        //     l.setBorder(new EmptyBorder(1, 0, 1, 4));
        //     l.setAlignmentX(Component.LEFT_ALIGNMENT);
        //     footerPanel.add(l);
        // }

        footerPanel.add(Box.createVerticalStrut(12));
        JPanel shortLine = new JPanel();
        shortLine.setBackground(new Color(70, 82, 90));
        shortLine.setPreferredSize(new Dimension(24, 1));
        shortLine.setMaximumSize(new Dimension(24, 1));
        shortLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerPanel.add(shortLine);

        footerPanel.add(Box.createVerticalStrut(10));
        JLabel tmdb = new JLabel("Powered by TMDB");
        tmdb.setForeground(new Color(88, 99, 108));
        tmdb.setFont(sans(Font.PLAIN, 9));
        tmdb.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerPanel.add(tmdb);

        sidebar.add(footerPanel);
        return sidebar;
    }

    private void addNavButton(JPanel parent, String label, IconKind kind, Runnable action) {
        JButton button = new JButton(label);
        styleButton(button, transparent, navText, null, 12, 0, 12);
        button.setFont(spaced(sans(Font.PLAIN, 13), 0.012f));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(13);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setPreferredSize(new Dimension(184, 40));
        button.setMinimumSize(new Dimension(40, 40));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setIcon(new VectorIcon(kind, 18, iconIdle,
                kind == IconKind.HOME || kind == IconKind.GRID
                        || kind == IconKind.HEART));

        navButtons.put(label, button);
        navKinds.put(label, kind);
        navActions.put(label, action);

        button.addActionListener(e -> navigateTo(label));

        parent.add(button);
        parent.add(Box.createVerticalStrut(4));
    }

    // --- collapsible sidebar ---

    private void toggleSidebar() {
        setSidebarExpanded(!sidebarExpanded);
    }

    private void setSidebarExpanded(boolean expand) {
        if (sidebarTimer != null) {
            sidebarTimer.stop();
        }
        sidebarExpanded = expand;

        if (!expand) {
            applySidebarContent(false);
        }

        final int target = expand ? SIDEBAR_FULL : SIDEBAR_RAIL;
        sidebarTimer = new javax.swing.Timer(14, null);
        sidebarTimer.addActionListener(e -> {
            int diff = target - sidebarWidth;
            if (Math.abs(diff) <= 28) {
                sidebarWidth = target;
                sidebarTimer.stop();
                if (expand) {
                    applySidebarContent(true);
                }
            } else {
                sidebarWidth += Integer.signum(diff) * 28;
            }
            sidebar.setPreferredSize(new Dimension(sidebarWidth, 0));
            getContentPane().revalidate();
            getContentPane().repaint();
        });
        sidebarTimer.start();
    }

    private void applySidebarContent(boolean expanded) {
        logoText.setVisible(expanded);
        libraryLabel.setVisible(expanded);
        footerPanel.setVisible(expanded);
        logoMark.setBorder(new EmptyBorder(0, expanded ? 0 : 7, 0, 0));

        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            JButton b = entry.getValue();
            if (expanded) {
                b.setText(entry.getKey());
                b.setHorizontalAlignment(SwingConstants.LEFT);
                b.setBorder(new EmptyBorder(0, 12, 0, 8));
                b.setToolTipText(null);
            } else {
                b.setText("");
                b.setHorizontalAlignment(SwingConstants.CENTER);
                b.setBorder(new EmptyBorder(0, 0, 0, 0));
                b.setToolTipText(entry.getKey());
            }
        }
        sidebar.revalidate();
        sidebar.repaint();
    }

    /** Wordmark: bold serif "Absolute" + italic green "Cinema". */
    private class LogoLabel extends JComponent {
        LogoLabel() {
            setPreferredSize(new Dimension(140, 30));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            String a = "Absolute";
            String b = "Cinema";
            float size = 19f;

            Font fa = serif(Font.BOLD, size);
            Font fb = serif(Font.ITALIC, size);
            int need = g2.getFontMetrics(fa).stringWidth(a)
                    + g2.getFontMetrics(fb).stringWidth(b) + 2;
            if (getWidth() > 0 && need > getWidth()) {
                size = size * getWidth() / need;
                fa = serif(Font.BOLD, size);
                fb = serif(Font.ITALIC, size);
            }

            FontMetrics ma = g2.getFontMetrics(fa);
            int base = (getHeight() + ma.getAscent() - ma.getDescent()) / 2;

            g2.setFont(fa);
            g2.setColor(textColor);
            g2.drawString(a, 0, base);

            g2.setFont(fb);
            g2.setColor(green);
            g2.drawString(b, ma.stringWidth(a) + 2, base);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------
    // TOP BAR
    // ---------------------------------------------------------

    private JPanel createTopBar() {
        JPanel top = new JPanel(new BorderLayout(20, 0));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(0, 0, 18, 0));

        // sidebar toggle
        JButton toggle = new JButton();
        styleButton(toggle, cardColor, textColor, borderColor, 16, 0, 0);
        toggle.setIcon(new VectorIcon(IconKind.MENU, 20, new Color(200, 207, 213), false));
        toggle.setPreferredSize(new Dimension(44, 44));
        toggle.setToolTipText("Show / hide sidebar");
        toggle.addActionListener(e -> toggleSidebar());

        // search pill
        RoundedPanel searchPanel = new RoundedPanel(cardColor, borderColor, 44);
        searchPanel.setLayout(new BorderLayout(10, 0));
        searchPanel.setBorder(new EmptyBorder(0, 16, 0, 16));
        searchPanel.setPreferredSize(new Dimension(400, 44));

        JLabel searchIcon = new JLabel(new VectorIcon(IconKind.SEARCH, 17, muted, false));
        searchIcon.setCursor(handCursor);
        searchIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                searchMovies();
            }
        });

        searchField.setOpaque(false);
        searchField.setBorder(null);
        searchField.setForeground(textColor);
        searchField.setCaretColor(green);
        searchField.setFont(sans(Font.PLAIN, 13));
        searchField.setToolTipText("Search movies by title");
        searchField.addActionListener(e -> searchMovies());

        searchPanel.add(searchIcon, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(toggle);
        left.add(Box.createHorizontalStrut(12));
        left.add(searchPanel);

        // right navigation
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 28, 0));
        right.setOpaque(false);

        TopTab discover = new TopTab("Discover", IconKind.COMPASS, "Home");
        TopTab watchlist = new TopTab("Watchlist", IconKind.BOOKMARK, "Watchlist");
        topTabs.put("Discover", discover);
        topTabs.put("Watchlist", watchlist);

        right.add(discover);
        right.add(watchlist);
        right.add(new AvatarButton());

        top.add(left, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);
        return top;
    }

    private class TopTab extends JLabel {
        private final IconKind kind;
        private boolean active;

        TopTab(String text, IconKind kind, String target) {
            super(text);
            this.kind = kind;
            setIconTextGap(9);
            setFont(spaced(sans(Font.PLAIN, 13), 0.012f));
            setBorder(new EmptyBorder(0, 2, 0, 6));
            setCursor(handCursor);
            refresh();
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    navigateTo(target);
                }
            });
        }

        void setActive(boolean value) {
            active = value;
            refresh();
            repaint();
        }

        private void refresh() {
            setForeground(active ? textColor : muted);
            setIcon(new VectorIcon(kind, 17, active ? green : muted, false));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = 42;
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (active) {
                g.setColor(green);
                g.fillRect(0, getHeight() - 2, getWidth(), 2);
            }
        }
    }

    private class AvatarButton extends JComponent {
        AvatarButton() {
            setPreferredSize(new Dimension(58, 36));
            setCursor(handCursor);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showAccountMenu(AvatarButton.this);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(48, 58, 66));
            g2.fillOval(0, 2, 32, 32);
            g2.setColor(new Color(70, 84, 94));
            g2.drawOval(0, 2, 31, 31);
            new VectorIcon(IconKind.USER, 18, new Color(190, 198, 205), true)
                    .paintIcon(this, g2, 7, 9);
            new VectorIcon(IconKind.CHEVRON, 14, muted, false)
                    .paintIcon(this, g2, 38, 11);
            g2.dispose();
        }
    }

    
private void showAccountMenu(Component invoker) {
    JPopupMenu menu = new JPopupMenu();
    menu.setBackground(cardColor);
    menu.setBorder(BorderFactory.createLineBorder(borderColor));

    String[] labels = {"Watchlist", "Watched", "Reviews"};
    IconKind[] icons = {
        IconKind.BOOKMARK,
        IconKind.CHECK,
        IconKind.CHAT
    };

    for (int i = 0; i < labels.length; i++) {
        String label = labels[i];

        JMenuItem item = new JMenuItem(label);
        item.setOpaque(true);
        item.setBackground(cardColor);
        item.setForeground(textColor);
        item.setFont(sans(Font.PLAIN, 13));
        item.setIcon(new VectorIcon(icons[i], 17, iconIdle, false));
item.setHorizontalAlignment(SwingConstants.LEFT);
item.setHorizontalTextPosition(SwingConstants.RIGHT);
item.setIconTextGap(16);
item.setBorder(new EmptyBorder(10, 3, 10, 16));
item.setMargin(new Insets(0, 0, 0, 0));
        item.setFocusPainted(false);

        item.addActionListener(e -> navigateTo(label));
        menu.add(item);
    }

    menu.show(
        invoker,
        invoker.getWidth() - menu.getPreferredSize().width,
        invoker.getHeight()
    );
}


    // ---------------------------------------------------------
    // BUTTON STYLING
    // ---------------------------------------------------------

    private void styleButton(JButton button, Color fill, Color foreground) {
        styleButton(button, fill, foreground, null, 12, 9, 16);
    }

    private void stylePill(JButton button, Color fill, Color foreground, Color outline) {
        styleButton(button, fill, foreground, outline, 0, 10, 22);
    }

    private void styleButton(JButton button, Color fill, Color foreground,
                             Color outline, int arc, int padV, int padH) {
        button.setUI(new RoundedButtonUI(arc, outline));
        button.setBackground(fill);
        button.setForeground(foreground);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setFont(spaced(sans(Font.BOLD, 12), 0.01f));
        button.setBorder(new EmptyBorder(padV, padH, padV, padH));
        button.setCursor(handCursor);
    }

    private static class RoundedButtonUI extends BasicButtonUI {
        private final int arc;
        private final Color outline;

        RoundedButtonUI(int arc, Color outline) {
            this.arc = arc;
            this.outline = outline;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setRolloverEnabled(true);
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = c.getWidth();
            int h = c.getHeight();
            int a = arc <= 0 ? h : arc;
            boolean hover = b.getModel().isRollover();
            Color fill = c.getBackground();

            if (fill.getAlpha() == 0) {
                if (hover) {
                    g2.setColor(new Color(255, 255, 255, 16));
                    g2.fillRoundRect(0, 0, w, h, a, a);
                }
            } else {
                if (hover) {
                    fill = shift(fill);
                }
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, a, a);
            }

            if (outline != null) {
                g2.setColor(hover ? shift(outline) : outline);
                g2.drawRoundRect(0, 0, w - 1, h - 1, a, a);
            }

            g2.dispose();
            super.paint(g, c);
        }

        private static Color shift(Color c) {
            int brightness = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
            if (brightness > 225) {
                return new Color(Math.max(0, c.getRed() - 22),
                        Math.max(0, c.getGreen() - 18),
                        Math.max(0, c.getBlue() - 15), c.getAlpha());
            }
            return new Color(
                    Math.min(255, c.getRed() + (int) ((255 - c.getRed()) * 0.14)),
                    Math.min(255, c.getGreen() + (int) ((255 - c.getGreen()) * 0.14)),
                    Math.min(255, c.getBlue() + (int) ((255 - c.getBlue()) * 0.14)),
                    c.getAlpha());
        }
    }

    // ---------------------------------------------------------
    // PAGE HELPERS
    // ---------------------------------------------------------

    private JPanel createPage(String title, String subtitle) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 0, 14, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(textColor);
        titleLabel.setFont(spaced(serif(Font.BOLD, 30), -0.008f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 0, 6));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(muted);
        subtitleLabel.setFont(spaced(sans(Font.PLAIN, 13), 0.012f));
        subtitleLabel.setBorder(new EmptyBorder(0, 0, 0, 6));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(subtitleLabel);
        return panel;
    }

    private JScrollPane createScroll(JComponent component) {
        JScrollPane scroll = new JScrollPane(component,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setViewportBorder(null);
        scroll.setBackground(background);
        scroll.getViewport().setBackground(background);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.getVerticalScrollBar().setBackground(background);
        return scroll;
    }

    private void stopHero() {
        if (activeHero != null) {
            activeHero.stop();
            activeHero = null;
        }
    }

    private void showPageContent(JComponent heading, JComponent body) {
        stopHero();
        pagePanel.removeAll();
        if (heading != null) {
            pagePanel.add(heading, BorderLayout.NORTH);
        }
        pagePanel.add(body, BorderLayout.CENTER);
        pagePanel.revalidate();
        pagePanel.repaint();
    }

    private ScrollPanel createMovieGrid() {
        ScrollPanel grid = new ScrollPanel(new FillGridLayout(150, 22, 30));
        grid.setBackground(background);
        grid.setBorder(new EmptyBorder(6, 0, 24, 0));
        return grid;
    }

    private void showGridMessage(JPanel grid, JPanel message) {
        grid.setLayout(new BorderLayout());
        grid.add(message, BorderLayout.NORTH);
    }

    private JPanel createCenteredMessage(String title, String subtitle) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(50, 20, 50, 20));

        JLabel heading = new JLabel(title);
        heading.setForeground(textColor);
        heading.setFont(serif(Font.BOLD, 22));
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel message = new JLabel(subtitle);
        message.setForeground(muted);
        message.setFont(sans(Font.PLAIN, 13));
        message.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(heading);
        panel.add(Box.createVerticalStrut(10));
        panel.add(message);
        return panel;
    }

    private JLabel sectionHeading(String text) {
        JLabel heading = new JLabel(text);
        heading.setForeground(textColor);
        heading.setFont(spaced(serif(Font.BOLD, 22), -0.006f));
        heading.setBorder(new EmptyBorder(0, 0, 0, 6));
        return heading;
    }

    // ---------------------------------------------------------
    // HOME / DISCOVER
    // ---------------------------------------------------------

    private static class HomeData {
        List<TmdbMovie> trending = new ArrayList<>();
        List<TmdbMovie> recommended = new ArrayList<>();
        Map<Integer, String> genres = new LinkedHashMap<>();
    }

    
private void showDiscoverPage() {
    currentPage = "Home";
    final int token = ++navToken;

    ScrollPanel sections = new ScrollPanel(null);
    sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
    sections.setBackground(background);

    showPageContent(null, createScroll(sections));
    setStatus("Loading cinematic discoveries...");

    new SwingWorker<HomeData, Void>() {
        @Override
        protected HomeData doInBackground() {
            HomeData data = new HomeData();

            try {
                data.trending =
                        parseMovies(TmdbClient.getTrendingMovies());
            } catch (Exception ignored) {
                data.trending = new ArrayList<>();
            }

            try {
                data.recommended =
                        parseMovies(TmdbClient.getTopRatedMovies());
            } catch (Exception ignored) {
                data.recommended = new ArrayList<>();
            }

            try {
                data.genres = TmdbClient.getMovieGenres();
            } catch (Exception ignored) {
                data.genres = new LinkedHashMap<>();
            }

            return data;
        }

        @Override
        protected void done() {
            if (token != navToken) {
                return;
            }

            try {
                HomeData data = get();
                sections.removeAll();

                List<TmdbMovie> featured = new ArrayList<>();

                for (TmdbMovie movie : data.trending) {
                    String path = movie.getBackdropPath();

                    if (path != null && !path.isBlank()) {
                        featured.add(movie);
                    }

                    if (featured.size() == 4) {
                        break;
                    }
                }

                if (featured.isEmpty() && !data.trending.isEmpty()) {
                    featured.addAll(data.trending.subList(
                            0, Math.min(4, data.trending.size())
                    ));
                }

                if (!featured.isEmpty()) {
                    HeroBanner hero = new HeroBanner(featured);
                    activeHero = hero;
                    sections.add(hero);
                    sections.add(Box.createVerticalStrut(8));
                }

                addMovieRow(sections, "Trending Now", data.trending,
                        () -> navigateTo("Discover"));

                addMovieRow(sections, "Recommended for You",
                        data.recommended,
                        () -> navigateTo("Top Rated"));

                addGenreSection(sections, data.genres);

                if (data.trending.isEmpty()
                        && data.recommended.isEmpty()) {
                    JLabel offlineMessage = new JLabel(
                            "<html><div style='text-align:center;'>"
                            + "<h2>Online discovery is unavailable</h2>"
                            + "Connect to the internet and configure a "
                            + "valid TMDB access token to load movies."
                            + "<br>Your local watchlist can still be used."
                            + "</div></html>"
                    );

                    offlineMessage.setForeground(textColor);
                    offlineMessage.setAlignmentX(Component.CENTER_ALIGNMENT);
                    offlineMessage.setBorder(
                            new EmptyBorder(50, 20, 50, 20)
                    );

                    sections.add(offlineMessage);
                }

                sections.revalidate();
                sections.repaint();

                if (data.trending.isEmpty()
                        && data.recommended.isEmpty()) {
                    setStatus("Offline mode: online movies unavailable.");
                } else {
                    setStatus("Discovery loaded.");
                }

            } catch (Exception e) {
                e.printStackTrace();
                setStatus("Could not finish loading movie discovery.");
            }
        }
    }.execute();
}


    private void addMovieRow(JPanel parent, String title,
                             List<TmdbMovie> list, Runnable seeAll) {
        if (list == null || list.isEmpty()) {
            return;
        }

        JPanel section = new JPanel(new BorderLayout(0, 16));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(new EmptyBorder(0, 0, 38, 0));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel seeAllLabel = new JLabel("See All  \u2192");
        seeAllLabel.setForeground(muted);
        seeAllLabel.setFont(spaced(sans(Font.PLAIN, 12), 0.012f));
        seeAllLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
        seeAllLabel.setCursor(handCursor);
        seeAllLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                seeAllLabel.setForeground(green);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                seeAllLabel.setForeground(muted);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                seeAll.run();
            }
        });

        header.add(sectionHeading(title), BorderLayout.WEST);
        header.add(seeAllLabel, BorderLayout.EAST);

        JPanel row = new JPanel(new RowLayout(118, 18));
        row.setOpaque(false);

        int limit = Math.min(list.size(), 14);
        for (int i = 0; i < limit; i++) {
            row.add(new MovieTile(list.get(i), 118));
        }

        section.add(header, BorderLayout.NORTH);
        section.add(row, BorderLayout.CENTER);
        parent.add(section);
    }

    private void addGenreSection(JPanel parent, Map<Integer, String> genres) {
        if (genres == null || genres.isEmpty()) {
            return;
        }

        JPanel section = new JPanel(new BorderLayout(0, 14));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(new EmptyBorder(0, 0, 24, 0));

        JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        chips.setOpaque(false);
        chips.setBorder(new EmptyBorder(0, -10, 0, 0));

        int count = 0;
        for (Map.Entry<Integer, String> entry : genres.entrySet()) {
            if (count++ >= 12) {
                break;
            }
            JButton chip = new JButton(entry.getValue());
            styleButton(chip, transparent, new Color(200, 207, 213),
                    new Color(58, 72, 81), 0, 7, 16);
            chip.setFont(spaced(sans(Font.PLAIN, 11), 0.015f));
            chip.addActionListener(e -> {
                showGenreMovies(entry.getKey(), entry.getValue());
                setActiveNavigation("Genres");
            });
            chips.add(chip);
        }

        section.add(sectionHeading("Genres"), BorderLayout.NORTH);
        section.add(chips, BorderLayout.CENTER);
        parent.add(section);
    }

    // ---------------------------------------------------------
    // HERO BANNER (cinematic, slow zoom + crossfade)
    // ---------------------------------------------------------

    private class HeroBanner extends JPanel {
        private static final long SLIDE_MS = 8000L;
        private static final long FADE_MS = 900L;
        private static final double ZOOM = 1.12;

        private final List<TmdbMovie> items;
        private final Map<Integer, BufferedImage> backdrops = new ConcurrentHashMap<>();
        private final Map<Integer, BufferedImage> bases = new HashMap<>();
        private final long[] loadedAt;
        private final TextBlock titleBlock = new TextBlock(2, 1.04f);
        private final TextBlock overviewBlock = new TextBlock(3, 1.4f);
        private final JLabel metaLabel = new JLabel();
        private final javax.swing.Timer anim;
        private final List<Rectangle> dotRects = new ArrayList<>();
        private final ComponentListener viewportListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                revalidate();
            }
        };

        private JViewport viewport;
        private int index = 0;
        private int prevIndex = -1;
        private double prevProgress = 1.0;
        private long slideStart;
        private long fadeStart;
        private BufferedImage overlay;
        private int overlayW;
        private int overlayH;
        private int baseW;
        private int baseH;

        HeroBanner(List<TmdbMovie> items) {
            this.items = items;
            this.loadedAt = new long[items.size()];

            setOpaque(false);
            setLayout(new BorderLayout());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setBorder(new EmptyBorder(0, 56, 58, 40));

            JLabel eyebrow = new JLabel("FEATURED");
            eyebrow.setForeground(green);
            eyebrow.setFont(spaced(sans(Font.BOLD, 11), 0.26f));
            eyebrow.setBorder(new EmptyBorder(0, 0, 0, 8));
            eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

            titleBlock.setForeground(Color.WHITE);
            titleBlock.setBlockWidth(560);
            titleBlock.setAlignmentX(Component.LEFT_ALIGNMENT);

            metaLabel.setForeground(new Color(196, 203, 210));
            metaLabel.setFont(spaced(sans(Font.PLAIN, 13), 0.02f));
            metaLabel.setIconTextGap(7);
            metaLabel.setBorder(new EmptyBorder(0, 0, 0, 6));
            metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            overviewBlock.setForeground(new Color(206, 211, 216));
            overviewBlock.setTextFont(sans(Font.PLAIN, 15), 0.012f);
            overviewBlock.setBlockWidth(500);
            overviewBlock.setAlignmentX(Component.LEFT_ALIGNMENT);

            JButton trailer = new JButton("Watch Trailer");
            styleButton(trailer, Color.WHITE, darkText, null, 0, 12, 26);
            trailer.setFont(spaced(sans(Font.BOLD, 14), 0.01f));
            trailer.setIcon(new VectorIcon(IconKind.PLAY, 14, darkText, true));
            trailer.setIconTextGap(10);
            trailer.addActionListener(e -> openTrailer(this.items.get(index)));

            JButton more = new JButton("More Info");
            styleButton(more, new Color(255, 255, 255, 18), Color.WHITE,
                    new Color(190, 198, 205), 0, 12, 26);
            more.setFont(spaced(sans(Font.PLAIN, 14), 0.01f));
            more.addActionListener(e -> showMovieDetails(this.items.get(index)));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            actions.setOpaque(false);
            actions.setAlignmentX(Component.LEFT_ALIGNMENT);
            actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            actions.add(trailer);
            actions.add(Box.createHorizontalStrut(12));
            actions.add(more);

            JPanel info = new JPanel();
            info.setOpaque(false);
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.add(Box.createVerticalGlue());
            info.add(eyebrow);
            info.add(Box.createVerticalStrut(14));
            info.add(titleBlock);
            info.add(Box.createVerticalStrut(14));
            info.add(metaLabel);
            info.add(Box.createVerticalStrut(16));
            info.add(overviewBlock);
            info.add(Box.createVerticalStrut(26));
            info.add(actions);

            JPanel west = new JPanel(new BorderLayout());
            west.setOpaque(false);
            west.setPreferredSize(new Dimension(590, 100));
            west.add(info, BorderLayout.CENTER);
            add(west, BorderLayout.WEST);

            anim = new javax.swing.Timer(40, e -> tick());

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    for (int i = 0; i < dotRects.size(); i++) {
                        if (dotRects.get(i).contains(e.getPoint())) {
                            showSlide(i);
                            return;
                        }
                    }
                }
            });

            for (int i = 0; i < items.size(); i++) {
                loadBackdrop(i);
            }

            showSlide(0);
            anim.start();
        }

        // hero fills well over half the visible page
        @Override
        public Dimension getPreferredSize() {
            JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(
                    JViewport.class, this);
            int vh = vp != null && vp.getHeight() > 0
                    ? vp.getHeight()
                    : AbsoluteCinemaGUI.this.getHeight() - 150;
            int h = Math.max(420, Math.min(740, (int) (vh * 0.78)));
            return new Dimension(700, h);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(300, getPreferredSize().height);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
            if (viewport != null) {
                viewport.addComponentListener(viewportListener);
            }
        }

        @Override
        public void removeNotify() {
            if (viewport != null) {
                viewport.removeComponentListener(viewportListener);
                viewport = null;
            }
            super.removeNotify();
        }

        void stop() {
            anim.stop();
        }

        private void tick() {
            if (!isShowing()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (items.size() > 1 && now - slideStart >= SLIDE_MS) {
                showSlide((index + 1) % items.size());
                return;
            }
            repaint();
        }

        private void loadBackdrop(int i) {
            String path = items.get(i).getBackdropPath();
            if (path == null || path.isBlank()) {
                return;
            }
            new SwingWorker<BufferedImage, Void>() {
                @Override
                protected BufferedImage doInBackground() throws Exception {
                    return fetchImage("https://image.tmdb.org/t/p/w1280" + path);
                }

                @Override
                protected void done() {
                    try {
                        BufferedImage image = get();
                        if (image != null) {
                            backdrops.put(i, image);
                            loadedAt[i] = System.currentTimeMillis();
                            repaint();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }.execute();
        }

        private void showSlide(int i) {
            long now = System.currentTimeMillis();

            if (i != index && slideStart > 0) {
                prevIndex = index;
                prevProgress = Math.min(1.0, (now - slideStart) / (double) SLIDE_MS);
            }
            index = i;
            slideStart = now;
            fadeStart = now;

            TmdbMovie movie = items.get(i);

            String name = safeTitle(movie.getTitle());
            float size = name.length() <= 16 ? 58f : name.length() <= 26 ? 46f : 36f;
            titleBlock.setTextFont(serif(Font.BOLD, size), -0.01f);
            titleBlock.setText(name);

            double vote = movie.getVote_average();
            String year = getYear(movie.getRelease_date());
            metaLabel.setIcon(new VectorIcon(IconKind.STAR, 14, green, true));
            metaLabel.setText((vote > 0 ? String.format(Locale.US, "%.1f", vote) : "NR")
                    + "    \u2022    " + year);

            String overview = movie.getOverview();
            if (overview == null || overview.isBlank()) {
                overview = "Discover more about this film and add it to your personal collection.";
            }
            overviewBlock.setText(overview);

            repaint();
        }

        private BufferedImage baseFor(int i, int w, int h) {
            if (baseW != w || baseH != h) {
                bases.clear();
                overlay = null;
                baseW = w;
                baseH = h;
            }
            BufferedImage base = bases.get(i);
            if (base == null) {
                BufferedImage src = backdrops.get(i);
                if (src == null) {
                    return null;
                }
                base = coverScale(src, (int) Math.round(w * ZOOM),
                        (int) Math.round(h * ZOOM), 0, 0.3);
                bases.put(i, base);
            }
            return base;
        }

        private void drawBackdrop(Graphics2D g2, int i, double progress,
                                  float alpha, int w, int h) {
            BufferedImage base = baseFor(i, w, h);
            if (base == null || alpha <= 0f) {
                return;
            }

            double z = 1.0 + (ZOOM - 1.0) * progress;
            double dw = w * z;
            double dh = h * z;
            double dx = -(dw - w) * 0.7;
            double dy = -(dh - h) * 0.35;

            AffineTransform at = new AffineTransform();
            at.translate(dx, dy);
            at.scale(dw / base.getWidth(), dh / base.getHeight());

            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
            g2.drawImage(base, at, null);
            g2.setComposite(old);
        }

        private void ensureOverlay(int w, int h) {
            if (overlay != null && overlayW == w && overlayH == h) {
                return;
            }

            BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = o.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // dark left side for the text
            g2.setPaint(new LinearGradientPaint(0, 0, w, 0,
                    new float[]{0f, 0.28f, 0.52f, 0.80f, 1f},
                    new Color[]{
                            withAlpha(background, 250),
                            withAlpha(background, 226),
                            withAlpha(background, 122),
                            withAlpha(background, 22),
                            withAlpha(background, 0)}));
            g2.fillRect(0, 0, w, h);

            // cinematic vignette
            float radius = (float) (Math.max(w, h) * 0.78);
            g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(w * 0.68f, h * 0.42f), radius,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{
                            withAlpha(background, 0),
                            withAlpha(background, 0),
                            withAlpha(background, 175)}));
            g2.fillRect(0, 0, w, h);

            // melt into the page below
            g2.setPaint(new GradientPaint(0, h * 0.5f, withAlpha(background, 0),
                    0, h, withAlpha(background, 255)));
            g2.fillRect(0, 0, w, h);

            // soft top shade
            g2.setPaint(new GradientPaint(0, 0, withAlpha(background, 140),
                    0, h * 0.22f, withAlpha(background, 0)));
            g2.fillRect(0, 0, w, h);

            // cool film grade
            g2.setColor(new Color(4, 34, 42, 34));
            g2.fillRect(0, 0, w, h);

            g2.dispose();
            overlay = o;
            overlayW = w;
            overlayH = h;
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g2.setColor(background);
            g2.fillRect(0, 0, w, h);

            long now = System.currentTimeMillis();
            double progress = Math.min(1.0, (now - slideStart) / (double) SLIDE_MS);

            long t0 = Math.max(fadeStart, loadedAt[index]);
            double raw = Math.min(1.0, (now - t0) / (double) FADE_MS);
            float alpha = (float) (raw * raw * (3 - 2 * raw));

            if (prevIndex >= 0) {
                drawBackdrop(g2, prevIndex, prevProgress, 1f, w, h);
                if (raw >= 1.0) {
                    prevIndex = -1;
                }
            }
            drawBackdrop(g2, index, progress, alpha, w, h);

            ensureOverlay(w, h);
            g2.drawImage(overlay, 0, 0, null);

            // soft rounded corners
            Area corners = new Area(new Rectangle2D.Double(0, 0, w, h));
            corners.subtract(new Area(new RoundRectangle2D.Double(0, 0, w, h, 22, 22)));
            g2.setColor(background);
            g2.fill(corners);

            // slide indicators
            dotRects.clear();
            int n = items.size();
            if (n > 1) {
                int gap = 8;
                int small = 7;
                int big = 26;
                int total = big + (n - 1) * small + (n - 1) * gap;
                int x = (w - total) / 2;
                int cy = h - 30;

                for (int i = 0; i < n; i++) {
                    int dw = i == index ? big : small;
                    g2.setColor(i == index
                            ? Color.WHITE : new Color(255, 255, 255, 95));
                    g2.fill(new RoundRectangle2D.Double(x, cy - 3.5, dw, 7, 7, 7));
                    dotRects.add(new Rectangle(x - 4, cy - 12, dw + 8, 24));
                    x += dw + gap;
                }
            }
            g2.dispose();
        }
    }

    // ---------------------------------------------------------
    // CATEGORY PAGES
    // ---------------------------------------------------------

    private void showPopularPage() {
        loadCategoryPage("Popular", TmdbClient::getPopularMovies);
    }

    private void showTopRatedPage() {
        loadCategoryPage("Top Rated", TmdbClient::getTopRatedMovies);
    }

    private void showNowPlayingPage() {
        loadCategoryPage("New Releases", TmdbClient::getNowPlayingMovies);
    }

    @FunctionalInterface
    private interface JsonLoader {
        String load() throws Exception;
    }

    private void loadCategoryPage(String title, JsonLoader loader) {
        currentPage = title;
        final int token = ++navToken;

        JPanel heading = createPage(title, "Explore films selected from TMDB.");

        ScrollPanel grid = createMovieGrid();
        showPageContent(heading, createScroll(grid));
        setStatus("Loading " + title + "...");

        new SwingWorker<List<TmdbMovie>, Void>() {
            @Override
            protected List<TmdbMovie> doInBackground() throws Exception {
                return parseMovies(loader.load());
            }

            @Override
            protected void done() {
                if (token != navToken) {
                    return;
                }
                try {
                    List<TmdbMovie> result = get();

                    if (result.isEmpty()) {
                        showGridMessage(grid, createCenteredMessage(
                                "No movies found", "Try another category."));
                    } else {
                        for (TmdbMovie movie : result) {
                            grid.add(new MovieTile(movie, 150));
                        }
                    }

                    grid.revalidate();
                    grid.repaint();
                    setStatus(result.size() + " movies loaded.");
                } catch (Exception e) {
                    showError("Could not load " + title + ".", e);
                    setStatus("Movie loading failed.");
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------
    // GENRES
    // ---------------------------------------------------------

    private void showGenresPage() {
        currentPage = "Genres";
        final int token = ++navToken;

        JPanel heading = createPage("Browse Genres",
                "Choose a genre to discover your next film.");

        int gap = 12;
        ScrollPanel genrePanel = new ScrollPanel(
                new WrapLayout(FlowLayout.LEFT, gap, gap));
        genrePanel.setBackground(background);
        genrePanel.setBorder(new EmptyBorder(5, -gap, 20, 0));

        showPageContent(heading, createScroll(genrePanel));
        setStatus("Loading genres...");

        new SwingWorker<Map<Integer, String>, Void>() {
            @Override
            protected Map<Integer, String> doInBackground() throws Exception {
                return TmdbClient.getMovieGenres();
            }

            @Override
            protected void done() {
                if (token != navToken) {
                    return;
                }
                try {
                    Map<Integer, String> genres = get();
                    genrePanel.removeAll();

                    for (Map.Entry<Integer, String> entry : genres.entrySet()) {
                        JButton button = new JButton(entry.getValue());
                        styleButton(button, cardColor, textColor, borderColor, 16, 10, 12);
                        button.setFont(spaced(sans(Font.PLAIN, 13), 0.012f));
                        button.setPreferredSize(new Dimension(175, 54));
                        button.addActionListener(e ->
                                showGenreMovies(entry.getKey(), entry.getValue()));
                        genrePanel.add(button);
                    }

                    genrePanel.revalidate();
                    genrePanel.repaint();
                    setStatus(genres.size() + " genres loaded.");
                } catch (Exception e) {
                    showError("Could not load movie genres.", e);
                    setStatus("Genre loading failed.");
                }
            }
        }.execute();
    }

    private void showGenreMovies(int genreId, String genreName) {
        currentPage = genreName;
        final int token = ++navToken;

        JPanel heading = createPage(genreName + " Movies",
                "Films in the " + genreName.toLowerCase() + " genre.");

        ScrollPanel grid = createMovieGrid();
        showPageContent(heading, createScroll(grid));
        setStatus("Loading " + genreName + "...");

        new SwingWorker<List<TmdbMovie>, Void>() {
            @Override
            protected List<TmdbMovie> doInBackground() throws Exception {
                return parseMovies(TmdbClient.discoverMoviesByGenre(genreId));
            }

            @Override
            protected void done() {
                if (token != navToken) {
                    return;
                }
                try {
                    List<TmdbMovie> result = get();

                    if (result.isEmpty()) {
                        showGridMessage(grid, createCenteredMessage(
                                "No films found", "Try browsing another genre."));
                    } else {
                        for (TmdbMovie movie : result) {
                            grid.add(new MovieTile(movie, 150));
                        }
                    }

                    grid.revalidate();
                    grid.repaint();
                    setStatus(result.size() + " movies loaded.");
                } catch (Exception e) {
                    showError("Could not load genre movies.", e);
                    setStatus("Genre loading failed.");
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------
    // SEARCH
    // ---------------------------------------------------------

    private void searchMovies() {
        String query = searchField.getText().trim();

        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Enter a movie title to search.", "Search",
                    JOptionPane.INFORMATION_MESSAGE);
            searchField.requestFocusInWindow();
            return;
        }

        currentPage = "Search Results";
        final int token = ++navToken;
        setActiveNavigation("");

        JPanel heading = createPage("Search Results",
                "Showing results for \"" + query + "\".");

        ScrollPanel grid = createMovieGrid();
        showPageContent(heading, createScroll(grid));
        setStatus("Searching TMDB...");

        new SwingWorker<List<TmdbMovie>, Void>() {
            @Override
            protected List<TmdbMovie> doInBackground() throws Exception {
                return parseMovies(TmdbClient.searchMovies(query));
            }

            @Override
            protected void done() {
                if (token != navToken) {
                    return;
                }
                try {
                    List<TmdbMovie> result = get();

                    if (result.isEmpty()) {
                        showGridMessage(grid, createCenteredMessage("No matching movies",
                                "Check the spelling or try a different title."));
                    } else {
                        for (TmdbMovie movie : result) {
                            grid.add(new MovieTile(movie, 150));
                        }
                    }

                    grid.revalidate();
                    grid.repaint();
                    setStatus(result.size() + " search results.");
                } catch (Exception e) {
                    showError("Movie search failed.", e);
                    setStatus("Search failed.");
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------
    // TMDB JSON PARSING
    // ---------------------------------------------------------

    private List<TmdbMovie> parseMovies(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            TmdbMovieResponse response = gson.fromJson(json, TmdbMovieResponse.class);

            if (response == null || response.results == null) {
                return new ArrayList<>();
            }

            return Arrays.asList(response.results);
        } catch (Exception e) {
            System.err.println("Could not parse TMDB response: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static class TmdbMovieResponse {
        TmdbMovie[] results;
    }

    // ---------------------------------------------------------
    // MOVIE TILE (9:16 poster that fills its column)
    // ---------------------------------------------------------

    private interface HeightForWidth {
        int heightForWidth(int width);
    }

    private class MovieTile extends JPanel implements HeightForWidth {
        private final int minWidth;
        private final PosterView poster = new PosterView(elevatedColor, 12);
        private final TextBlock title = new TextBlock(2, 1.22f);
        private final JLabel year;
        private final JLabel rating;

        MovieTile(TmdbMovie movie, int minWidth) {
            super(null);
            this.minWidth = minWidth;
            setOpaque(false);
            setCursor(handCursor);

            poster.setTextStyle(sans(Font.PLAIN, 10), muted);
            String posterPath = movie.getPoster_path();
            if (posterPath != null && !posterPath.isBlank()) {
                loadPosterView(poster, imageBase + posterPath);
            } else {
                poster.setMessage("No poster");
            }

            title.setTextFont(sans(Font.PLAIN, 12), 0.012f);
            title.setForeground(textColor);
            title.setText(safeTitle(movie.getTitle()));

            year = new JLabel(getYear(movie.getRelease_date()));
            year.setForeground(muted);
            year.setFont(spaced(sans(Font.PLAIN, 11), 0.02f));
            year.setBorder(new EmptyBorder(0, 0, 0, 4));

            double vote = movie.getVote_average();
            rating = new JLabel(
                    vote > 0 ? String.format(Locale.US, "%.1f", vote) : "NR",
                    new VectorIcon(IconKind.STAR, 12, green, true),
                    SwingConstants.LEFT);
            rating.setIconTextGap(5);
            rating.setForeground(new Color(214, 220, 225));
            rating.setFont(spaced(sans(Font.BOLD, 11), 0.02f));
            rating.setBorder(new EmptyBorder(0, 0, 0, 4));

            add(poster);
            add(title);
            add(year);
            add(rating);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showMovieDetails(movie);
                }
            });
        }

        @Override
        public int heightForWidth(int w) {
            int ph = (int) Math.round(w * 16.0 / 9.0);
            return ph + 11 + title.heightFor(w) + 4
                    + year.getPreferredSize().height + 5
                    + rating.getPreferredSize().height;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(minWidth, heightForWidth(minWidth));
        }

        @Override
        public void doLayout() {
            int w = getWidth();
            int ph = (int) Math.round(w * 16.0 / 9.0);
            poster.setBounds(0, 0, w, ph);

            int y = ph + 11;
            int th = title.heightFor(w);
            title.setBounds(0, y, w, th);
            y += th + 4;

            int yh = year.getPreferredSize().height;
            year.setBounds(0, y, w, yh);
            y += yh + 5;

            rating.setBounds(0, y, w, rating.getPreferredSize().height);
        }
    }

// =========================================================
    // MOVIE DETAILS PAGE  (paste inside AbsoluteCinemaGUI, e.g. just
    // above the "CUSTOM COMPONENTS" section). It replaces the old
    // JDialog-based showMovieDetails(TmdbMovie) method.
    // =========================================================

    private static final String TMDB_IMG = "https://image.tmdb.org/t/p/";
    private final Color glass = new Color(14, 22, 28, 215);
    private final Color starYellow = new Color(255, 196, 40);
    private final Map<Integer, DetailsData> detailsCache = new ConcurrentHashMap<>();
    private List<TmdbMovie> trendingCache = null;

    private static class DetailsData {
        final JsonObject root;
        final List<TmdbMovie> trending;

        DetailsData(JsonObject root, List<TmdbMovie> trending) {
            this.root = root;
            this.trending = trending;
        }
    }

    // ---- tiny JSON helpers (never throw, never return null strings) ----

    private static String js(JsonObject o, String k) {
        try {
            if (o == null || !o.has(k) || o.get(k).isJsonNull()) {
                return "";
            }
            return o.get(k).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static double jd(JsonObject o, String k) {
        try {
            String s = js(o, k);
            return s.isEmpty() ? 0 : Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int ji(JsonObject o, String k) {
        return (int) jd(o, k);
    }

    private static JsonArray ja(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray()
                ? o.getAsJsonArray(k) : new JsonArray();
    }

    private static JsonObject jo(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject()
                ? o.getAsJsonObject(k) : null;
    }

    private static String formatRuntime(int min) {
        if (min <= 0) {
            return "";
        }
        int h = min / 60;
        int m = min % 60;
        if (h == 0) {
            return m + "m";
        }
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    private static String compactCount(int n) {
        if (n >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 1000) {
            return Math.round(n / 1000.0) + "K";
        }
        return String.valueOf(n);
    }

    private static String formatDate(String iso) {
        try {
            return java.time.LocalDate.parse(iso).format(
                    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
        } catch (Exception e) {
            return "";
        }
    }

    // ---- library helpers (reuse the existing 'movies' list) ----

    private Movie findLibraryMovie(TmdbMovie t) {
        String title = safeTitle(t.getTitle());
        int year = parseYear(t.getRelease_date());
        for (Movie m : movies) {
            if (m.getTitle() != null && m.getTitle().equalsIgnoreCase(title)
                    && m.getReleaseYear() == year) {
                return m;
            }
        }
        return null;
    }

    private Movie ensureLibraryMovie(TmdbMovie t) {
        Movie m = findLibraryMovie(t);
        if (m == null) {
            m = new Movie(safeTitle(t.getTitle()), parseYear(t.getRelease_date()), "Unknown");
            m.setWatched(false);
            m.setRating(0.0);
            m.setPosterPath(t.getPoster_path());
            movies.add(m);
        }
        return m;
    }

    // ---- async image helper (shares rawCache with the posters) ----

    private void loadImageAsync(String url, java.util.function.Consumer<BufferedImage> done) {
        BufferedImage cached = rawCache.get(url);
        if (cached != null) {
            done.accept(cached);
            return;
        }
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return fetchImage(url);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage img = get();
                    if (img != null) {
                        rawCache.put(url, img);
                        done.accept(img);
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    // ---- entry point: called by every poster/tile/hero click ----

    private void showMovieDetails(TmdbMovie movie) {
        final int id = movie.getId();
        currentPage = safeTitle(movie.getTitle());
        final int token = ++navToken;
        setActiveNavigation("");

        DetailsData cached = detailsCache.get(id);
        if (cached != null) {
            showPageContent(null, createScroll(new DetailsPage(movie, cached)));
            setStatus(safeTitle(movie.getTitle()));
            return;
        }

        showPageContent(null, createCenteredMessage("Loading film details\u2026",
                safeTitle(movie.getTitle())));
        setStatus("Loading " + safeTitle(movie.getTitle()) + "...");

        new SwingWorker<DetailsData, Void>() {
            @Override
            protected DetailsData doInBackground() throws Exception {
                // ONE request: details + credits + videos + similar + release dates + reviews
                JsonObject root = gson.fromJson(TmdbClient.getMovieDetails(id), JsonObject.class);
                if (root == null) {
                    throw new IllegalStateException("Empty response from TMDB");
                }
                if (trendingCache == null) {
                    try {
                        trendingCache = parseMovies(TmdbClient.getTrendingMovies());
                    } catch (Exception ignored) {
                        trendingCache = new ArrayList<>();
                    }
                }
                return new DetailsData(root, trendingCache);
            }

            @Override
            protected void done() {
                if (token != navToken) {
                    return;
                }
                try {
                    DetailsData data = get();
                    detailsCache.put(id, data);
                    showPageContent(null, createScroll(new DetailsPage(movie, data)));
                    setStatus(safeTitle(movie.getTitle()));
                } catch (Exception e) {
                    e.printStackTrace();
                    JPanel error = createCenteredMessage("Couldn't load this film",
                            "Check your connection and try again.");
                    error.add(Box.createVerticalStrut(18));
                    JButton retry = new JButton("Retry");
                    stylePill(retry, green, darkText, null);
                    retry.setAlignmentX(Component.CENTER_ALIGNMENT);
                    retry.addActionListener(ev -> showMovieDetails(movie));
                    error.add(retry);
                    showPageContent(null, error);
                    setStatus("Movie loading failed.");
                }
            }
        }.execute();
    }

    // ---- small shared widgets ----

    private JPanel verticalPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JLabel h2(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(textColor);
        l.setFont(spaced(serif(Font.BOLD, 18), -0.004f));
        l.setBorder(new EmptyBorder(0, 0, 0, 6));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel linkLabel(String text, Runnable action) {
        JLabel l = new JLabel(text);
        l.setForeground(muted);
        l.setFont(spaced(sans(Font.PLAIN, 12), 0.012f));
        l.setBorder(new EmptyBorder(0, 0, 0, 4));
        l.setCursor(handCursor);
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                l.setForeground(green);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                l.setForeground(muted);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
        return l;
    }

    private static class WidthPanel extends JPanel {
        private final int fixedWidth;

        WidthPanel(int fixedWidth) {
            this.fixedWidth = fixedWidth;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.width = fixedWidth;
            return d;
        }
    }

    /** Round profile picture with neutral placeholder. */
    private class AvatarView extends JComponent {
        private final int size;
        private BufferedImage scaled;

        AvatarView(int size, String path) {
            this.size = size;
            setPreferredSize(new Dimension(size, size));
            if (path != null && !path.isBlank()) {
                loadImageAsync(TMDB_IMG + "w185" + path, img -> {
                    scaled = coverScale(img, size, size, size, 0.3);
                    repaint();
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (scaled != null) {
                g2.drawImage(scaled, 0, 0, null);
            } else {
                g2.setColor(new Color(40, 50, 58));
                g2.fillOval(0, 0, size, size);
                new VectorIcon(IconKind.USER, size / 2, new Color(150, 160, 168), true)
                        .paintIcon(this, g2, size / 4, size / 4);
            }
            g2.setColor(new Color(90, 106, 116));
            g2.drawOval(0, 0, size - 1, size - 1);
            g2.dispose();
        }
    }

    /** Cast tile: ~1:1 portrait, name, character. */
    private class CastCard extends JPanel implements HeightForWidth {
        private final PosterView photo = new PosterView(elevatedColor, 6);
        private final TextBlock name = new TextBlock(1, 1.2f);
        private final TextBlock role = new TextBlock(1, 1.2f);

        CastCard(String personName, String character, String path) {
            super(null);
            setOpaque(false);
            photo.setTextStyle(sans(Font.PLAIN, 10), muted);
            if (path != null && !path.isBlank()) {
                loadPosterView(photo, TMDB_IMG + "w185" + path);
            } else {
                photo.setMessage("No photo");
            }
            name.setTextFont(sans(Font.PLAIN, 12), 0.01f);
            name.setForeground(textColor);
            name.setText(personName);
            role.setTextFont(sans(Font.PLAIN, 10), 0.01f);
            role.setForeground(muted);
            role.setText(character);
            add(photo);
            add(name);
            add(role);
        }

        @Override
        public int heightForWidth(int w) {
            return w + 8 + name.heightFor(w) + 2 + role.heightFor(w);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, heightForWidth(100));
        }

        @Override
        public void doLayout() {
            int w = getWidth();
            photo.setBounds(0, 0, w, w);
            int y = w + 8;
            int nh = name.heightFor(w);
            name.setBounds(0, y, w, nh);
            y += nh + 2;
            role.setBounds(0, y, w, role.heightFor(w));
        }
    }

    // ---------------------------------------------------------
    // THE PAGE
    // ---------------------------------------------------------

    private class DetailsPage extends ScrollPanel {
        private static final int HERO_H = 600;

        private final TmdbMovie movie;
        private final DetailsData data;
        private final JsonObject root;
        private final int movieId;
        private final String title;

        private final String[] tabNames = {"Overview", "Cast & Crew", "Reviews", "Similar Movies"};
        private final JPanel[] tabCards = new JPanel[4];
        private final DetailTab[] tabLabels = new DetailTab[4];
        private final JPanel tabHolder = new JPanel(new BorderLayout());
        private final TextBlock titleBlock = new TextBlock(2, 1.04f);
        private final TextBlock overviewBlock = new TextBlock(4, 1.45f);
        private final WidthPanel sideHolder = new WidthPanel(306);
        private final List<TmdbMovie> similar = new ArrayList<>();
        private String similarTitle = "Similar Movies";

        private JButton trailerBtn;
        private JButton watchBtn;
        
        private boolean sideBelow = false;
        private int lastHeroW = -1;

        private BufferedImage backdrop;
        private BufferedImage scaled;
        private BufferedImage overlay;
        private int scaledW;
        private int scaledH;
        private int overlayW;
        private int overlayH;

        DetailsPage(TmdbMovie movie, DetailsData data) {
            super(new BorderLayout(24, 0));
            this.movie = movie;
            this.data = data;
            this.root = data.root;
            this.movieId = movie.getId();
            String t = js(root, "title");
            this.title = t.isEmpty() ? safeTitle(movie.getTitle()) : t;

            setBackground(background);
            setBorder(new EmptyBorder(0, 0, 32, 0));

            JsonObject sim = jo(root, "similar");
            if (sim != null) {
                for (TmdbMovie m : parseMovies(sim.toString())) {
                    if (m.getId() != movieId) {
                        similar.add(m);
                    }
                }
            }
            if (similar.isEmpty()) {
                similarTitle = "Recommended";
                for (TmdbMovie m : data.trending) {
                    if (m.getId() != movieId) {
                        similar.add(m);
                    }
                }
            }

            JPanel column = verticalPanel();
            column.setBorder(new EmptyBorder(0, 40, 0, 0));
            column.setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(buildHero());
            column.add(buildTabs());
            tabHolder.setOpaque(false);
            tabHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(tabHolder);

            tabCards[0] = buildOverviewCard();
            tabCards[1] = buildCastCard();
            tabCards[2] = buildReviewsCard();
            tabCards[3] = buildSimilarCard();
            setTab(0);
            refreshLibraryButtons();

            JPanel main = new JPanel(new BorderLayout());
            main.setOpaque(false);
            main.add(column, BorderLayout.NORTH);
            add(main, BorderLayout.CENTER);

            buildSide();
            add(sideHolder, BorderLayout.EAST);

            titleBlock.setBlockWidth(460);
            overviewBlock.setBlockWidth(440);

            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    boolean narrow = getWidth() > 0 && getWidth() < 1100;
                    if (narrow != sideBelow) {
                        sideBelow = narrow;
                        remove(sideHolder);
                        sideHolder.setBorder(new EmptyBorder(narrow ? 8 : 257,
                                narrow ? 40 : 0, 0, 0));
                        add(sideHolder, narrow ? BorderLayout.SOUTH : BorderLayout.EAST);
                        revalidate();
                    }
                    applyHeroWidths();
                }
            });

            String bd = js(root, "backdrop_path");
            if (bd.isEmpty() && movie.getBackdropPath() != null) {
                bd = movie.getBackdropPath();
            }
            if (!bd.isBlank()) {
                loadBackdrop(bd);
            }
        }

        private void applyHeroWidths() {
            int avail = getWidth() - (sideBelow ? 0 : 306 + 24) - 40 - 212 - 30 - 12;
            int w = Math.max(300, Math.min(720, avail));
            if (w != lastHeroW) {
                lastHeroW = w;
                titleBlock.setBlockWidth(w);
                overviewBlock.setBlockWidth(Math.min(w, 620));
            }
        }

        // ---------- backdrop ----------

        private void loadBackdrop(String path) {
            new SwingWorker<BufferedImage, Void>() {
                @Override
                protected BufferedImage doInBackground() throws Exception {
                    String key = TMDB_IMG + "original" + path;
                    BufferedImage img = rawCache.get(key);
                    if (img != null) {
                        return img;
                    }
                    try {
                        img = fetchImage(key);
                    } catch (Exception ignored) {
                    }
                    if (img == null) {
                        img = fetchImage(TMDB_IMG + "w1280" + path);
                    }
                    if (img != null && img.getWidth() > 1920) {
                        int nh = img.getHeight() * 1920 / img.getWidth();
                        img = coverScale(img, 1920, nh, 0, 0.5);
                    }
                    if (img != null) {
                        rawCache.put(key, img);
                    }
                    return img;
                }

                @Override
                protected void done() {
                    try {
                        BufferedImage img = get();
                        if (img != null) {
                            backdrop = img;
                            scaled = null;
                            repaint();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }.execute();
        }

        private void ensureOverlay(int w, int h) {
            if (overlay != null && overlayW == w && overlayH == h) {
                return;
            }
            BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = o.createGraphics();
            g2.setPaint(new LinearGradientPaint(0, 0, w, 0,
                    new float[]{0f, 0.35f, 0.65f, 1f},
                    new Color[]{withAlpha(background, 245), withAlpha(background, 205),
                            withAlpha(background, 95), withAlpha(background, 25)}));
            g2.fillRect(0, 0, w, h);
            g2.setPaint(new GradientPaint(0, h * 0.30f, withAlpha(background, 0),
                    0, h, withAlpha(background, 255)));
            g2.fillRect(0, 0, w, h);
            g2.setPaint(new GradientPaint(0, 0, withAlpha(background, 130),
                    0, h * 0.2f, withAlpha(background, 0)));
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(4, 34, 42, 30));
            g2.fillRect(0, 0, w, h);
            g2.dispose();
            overlay = o;
            overlayW = w;
            overlayH = h;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = Math.min(getHeight(), HERO_H);
            if (w <= 0 || h <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(new RoundRectangle2D.Double(0, 0, w, h, 22, 22));
            if (backdrop != null) {
                if (scaled == null || scaledW != w || scaledH != h) {
                    scaled = coverScale(backdrop, w, h, 0, 0.25);
                    scaledW = w;
                    scaledH = h;
                }
                g2.drawImage(scaled, 0, 0, null);
            } else {
                g2.setPaint(new GradientPaint(0, 0, new Color(22, 38, 46), w, h, background));
                g2.fillRect(0, 0, w, h);
            }
            ensureOverlay(w, h);
            g2.drawImage(overlay, 0, 0, null);
            g2.dispose();
        }

        // ---------- hero ----------

        private JPanel buildHero() {
            JPanel hero = new JPanel(new BorderLayout(30, 0));
            hero.setOpaque(false);
            hero.setAlignmentX(Component.LEFT_ALIGNMENT);
            hero.setBorder(new EmptyBorder(40, 0, 28, 0));

            // poster (2:3)
            PosterView poster = new PosterView(cardColor, 7);
            poster.setTextStyle(sans(Font.PLAIN, 11), muted);
            String pp = js(root, "poster_path");
            if (pp.isEmpty() && movie.getPoster_path() != null) {
                pp = movie.getPoster_path();
            }
            if (!pp.isBlank()) {
                loadPosterView(poster, TMDB_IMG + "w500" + pp);
            } else {
                poster.setMessage("No poster");
            }
            JPanel posterFrame = new JPanel(new BorderLayout()) {
                @Override
                protected void paintChildren(Graphics g) {
                    super.paintChildren(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(255, 255, 255, 46));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
                    g2.dispose();
                }
            };
            posterFrame.setOpaque(false);
            posterFrame.setPreferredSize(new Dimension(212, 320));
            posterFrame.add(poster, BorderLayout.CENTER);
            JPanel west = new JPanel(new BorderLayout());
            west.setOpaque(false);
            west.add(posterFrame, BorderLayout.NORTH);

            // info column
            JPanel info = verticalPanel();

            titleBlock.setTextFont(serif(Font.BOLD,
                    title.length() <= 20 ? 46f : title.length() <= 32 ? 38f : 30f), -0.008f);
            titleBlock.setForeground(Color.WHITE);
            titleBlock.setText(title);
            info.add(titleBlock);
            info.add(Box.createVerticalStrut(12));

            List<String> parts = new ArrayList<>();
            int year = parseYear(js(root, "release_date"));
            if (year > 0) {
                parts.add(String.valueOf(year));
            }
            String rt = formatRuntime(ji(root, "runtime"));
            if (!rt.isEmpty()) {
                parts.add(rt);
            }
            String cert = certification();
            if (!cert.isEmpty()) {
                parts.add(cert);
            }
            if (!parts.isEmpty()) {
                JLabel meta = new JLabel(String.join("  \u2022  ", parts));
                meta.setForeground(new Color(200, 207, 213));
                meta.setFont(spaced(sans(Font.PLAIN, 15), 0.015f));
                meta.setAlignmentX(Component.LEFT_ALIGNMENT);
                info.add(meta);
                info.add(Box.createVerticalStrut(14));
            }

            JPanel chips = rowPanel();
            int shown = 0;
            for (JsonElement ge : ja(root, "genres")) {
                String g = js(ge.getAsJsonObject(), "name");
                if (g.isEmpty() || shown++ >= 4) {
                    continue;
                }
                if (chips.getComponentCount() > 0) {
                    chips.add(Box.createHorizontalStrut(8));
                }
                chips.add(chip(g));
            }
            if (chips.getComponentCount() > 0) {
                info.add(chips);
                info.add(Box.createVerticalStrut(18));
            }

            double vote = jd(root, "vote_average");
            if (vote > 0) {
                JPanel rating = rowPanel();
                JLabel score = new JLabel(String.format(Locale.US, "%.1f", vote),
                        new VectorIcon(IconKind.STAR, 20, starYellow, true), SwingConstants.LEFT);
                score.setIconTextGap(8);
                score.setForeground(Color.WHITE);
                score.setFont(spaced(sans(Font.BOLD, 20), 0.01f));
                JLabel outOf = new JLabel("/10");
                outOf.setForeground(muted);
                outOf.setFont(sans(Font.PLAIN, 14));
                rating.add(score);
                rating.add(Box.createHorizontalStrut(3));
                rating.add(outOf);
                int votes = ji(root, "vote_count");
                if (votes > 0) {
                    JLabel count = new JLabel("(" + compactCount(votes) + ")");
                    count.setForeground(muted);
                    count.setFont(sans(Font.PLAIN, 12));
                    rating.add(Box.createHorizontalStrut(10));
                    rating.add(count);
                }
                info.add(rating);
                info.add(Box.createVerticalStrut(20));
            }

            trailerBtn = heroButton("Watch Trailer", IconKind.PLAY, darkText, green, darkText, null);
            trailerBtn.addActionListener(e -> playTrailer());
            watchBtn = heroButton("Add to Watchlist", IconKind.PLUS, textColor, transparent,
                    textColor, new Color(110, 122, 131));
            watchBtn.addActionListener(e -> toggleWatchlist());

            

JButton watchedBtn = heroButton(
        "Watched",
        IconKind.CHECK,
        textColor,
        transparent,
        textColor,
        new Color(110, 122, 131)
);



Movie existing = findLibraryMovie(movie);
if (existing != null && existing.isWatched()) {
    watchedBtn.setText("Watched");
    watchedBtn.setIcon(new VectorIcon(IconKind.CHECK, 15, green, true));
}






if (existing != null && existing.isWatched()) {
    watchedBtn.setText("Watched");
    watchedBtn.setIcon(new VectorIcon(IconKind.CHECK, 15, green, true));
}


            JPanel actions = rowPanel();
            actions.add(trailerBtn);
            actions.add(Box.createHorizontalStrut(12));
            actions.add(watchBtn);
            
            actions.add(Box.createHorizontalStrut(12));
            info.add(actions);

            String overview = js(root, "overview");
            if (overview.isBlank()) {
                overview = movie.getOverview() == null ? "" : movie.getOverview();
            }
            if (!overview.isBlank()) {
                info.add(Box.createVerticalStrut(22));
                overviewBlock.setTextFont(sans(Font.PLAIN, 14), 0.012f);
                overviewBlock.setForeground(new Color(206, 211, 216));
                overviewBlock.setText(overview);
                info.add(overviewBlock);
            }

            JPanel infoHolder = new JPanel(new BorderLayout());
            infoHolder.setOpaque(false);
            infoHolder.add(info, BorderLayout.NORTH);

            hero.add(west, BorderLayout.WEST);
            hero.add(infoHolder, BorderLayout.CENTER);
            return hero;
        }

        private JPanel rowPanel() {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
            p.setOpaque(false);
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            return p;
        }

        private JPanel chip(String text) {
            RoundedPanel chip = new RoundedPanel(new Color(255, 255, 255, 14),
                    new Color(84, 100, 110), 30);
            chip.setLayout(new BorderLayout());
            JLabel l = new JLabel(text);
            l.setForeground(new Color(214, 220, 225));
            l.setFont(spaced(sans(Font.PLAIN, 12), 0.012f));
            l.setBorder(new EmptyBorder(5, 14, 5, 14));
            chip.add(l, BorderLayout.CENTER);
            chip.setMaximumSize(chip.getPreferredSize());
            return chip;
        }

        private JButton heroButton(String text, IconKind icon, Color iconColor,
                                   Color fill, Color fg, Color outline) {
            JButton b = new JButton(text);
            styleButton(b, fill, fg, outline, 0, 0, 22);
            b.setFont(spaced(sans(Font.BOLD, 13), 0.01f));
            b.setIcon(new VectorIcon(icon, 15, iconColor, icon == IconKind.PLAY));
            b.setIconTextGap(9);
            Dimension d = b.getPreferredSize();
            b.setPreferredSize(new Dimension(d.width, 44));
            b.setMaximumSize(new Dimension(d.width, 44));
            return b;
        }

        private JButton roundButton(IconKind icon) {
            JButton b = new JButton();
            styleButton(b, transparent, textColor, new Color(110, 122, 131), 0, 0, 0);
            b.setIcon(new VectorIcon(icon, 18, textColor, icon == IconKind.MORE));
            b.setPreferredSize(new Dimension(44, 44));
            b.setMaximumSize(new Dimension(44, 44));
            return b;
        }

        private String certification() {
            String any = "";
            for (JsonElement e : ja(jo(root, "release_dates"), "results")) {
                JsonObject country = e.getAsJsonObject();
                for (JsonElement r : ja(country, "release_dates")) {
                    String cert = js(r.getAsJsonObject(), "certification");
                    if (cert.isBlank()) {
                        continue;
                    }
                    if ("US".equals(js(country, "iso_3166_1"))) {
                        return cert;
                    }
                    if (any.isEmpty()) {
                        any = cert;
                    }
                }
            }
            return any;
        }

        // ---------- hero actions ----------

        

private void refreshLibraryButtons() {
    Movie lib = findLibraryMovie(movie);
    boolean inList = lib != null;

    watchBtn.setText(inList ? "In Watchlist" : "Add to Watchlist");
    watchBtn.setForeground(inList ? green : textColor);
    watchBtn.setIcon(new VectorIcon(
            inList ? IconKind.CHECK : IconKind.PLUS,
            15,
            inList ? green : textColor,
            false
    ));

    watchBtn.setPreferredSize(null);
    Dimension d = watchBtn.getPreferredSize();
    watchBtn.setPreferredSize(new Dimension(d.width, 44));
    watchBtn.setMaximumSize(new Dimension(d.width, 44));

    Container parent = watchBtn.getParent();
    if (parent != null) {
        parent.revalidate();
        parent.repaint();
    }
}



        private void toggleWatchlist() {
            Movie lib = findLibraryMovie(movie);
            if (lib == null) {
                ensureLibraryMovie(movie);
                saveMovies();
                setStatus("Added " + title + " to your watchlist.");
            } else {
                movies.remove(lib);
                saveMovies();
                setStatus("Removed " + title + " from your library.");
            }
            refreshLibraryButtons();
        }

        
/** Toggles the liked status independently of the watchlist. */


private void toggleLike() {
    Movie lib = findLibraryMovie(movie);

    if (lib == null) {
        setStatus("Like this movie after adding it to your watchlist.");
        return;
    }

    lib.setLiked(!lib.isLiked());

    saveMovies();
    refreshLibraryButtons();

    setStatus(lib.isLiked()
            ? "Added " + movie.getTitle() + " to liked movies."
            : "Removed " + movie.getTitle() + " from liked movies.");
}



        private void playTrailer() {
            String bestKey = null;
            double bestScore = -1;
            for (JsonElement e : ja(jo(root, "videos"), "results")) {
                JsonObject v = e.getAsJsonObject();
                if (!"YouTube".equalsIgnoreCase(js(v, "site")) || js(v, "key").isEmpty()) {
                    continue;
                }
                double score = "Trailer".equalsIgnoreCase(js(v, "type")) ? 2
                        : "Teaser".equalsIgnoreCase(js(v, "type")) ? 0.5 : 0;
                if ("true".equals(js(v, "official"))) {
                    score += 1;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestKey = js(v, "key");
                }
            }
            if (bestKey == null) {
                String old = trailerBtn.getText();
                trailerBtn.setText("No trailer available");
                javax.swing.Timer t = new javax.swing.Timer(2200, e -> trailerBtn.setText(old));
                t.setRepeats(false);
                t.start();
                return;
            }
            browse("https://www.youtube.com/watch?v=" + bestKey);
        }

        private void browse(String url) {
            try {
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                } else {
                    setStatus("Could not open a browser on this system.");
                }
            } catch (Exception ex) {
                showError("Could not open the link.", ex);
            }
        }

        private void copy(String text, String what) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
            setStatus(what + " copied to clipboard.");
        }

        private void showMoreMenu(Component invoker) {
            final String link = "https://www.themoviedb.org/movie/" + movieId;
            JPopupMenu menu = new JPopupMenu();
            menu.setBackground(cardColor);
            menu.setBorder(BorderFactory.createLineBorder(borderColor));
            String[] labels = {"Copy title", "Copy TMDB link", "Open on TMDB"};
            Runnable[] actions = {
                    () -> copy(title, "Title"),
                    () -> copy(link, "Link"),
                    () -> browse(link)};
            for (int i = 0; i < labels.length; i++) {
                JMenuItem item = new JMenuItem(labels[i]);
                item.setOpaque(true);
                item.setBackground(cardColor);
                item.setForeground(textColor);
                item.setFont(sans(Font.PLAIN, 13));
                item.setBorder(new EmptyBorder(9, 18, 9, 28));
                final Runnable action = actions[i];
                item.addActionListener(e -> action.run());
                menu.add(item);
            }
            menu.show(invoker, 0, invoker.getHeight() + 4);
        }

        // ---------- tabs ----------

        private JPanel buildTabs() {
            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(borderColor);
                    g.fillRect(0, getHeight() - 1, getWidth(), 1);
                }
            };
            bar.setOpaque(false);
            bar.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (int i = 0; i < tabNames.length; i++) {
                tabLabels[i] = new DetailTab(tabNames[i], i);
                bar.add(tabLabels[i]);
            }
            return bar;
        }

        private void setTab(int i) {
            for (int k = 0; k < tabLabels.length; k++) {
                tabLabels[k].setActive(k == i);
            }
            tabHolder.removeAll();
            tabHolder.add(tabCards[i], BorderLayout.CENTER);
            tabHolder.revalidate();
            tabHolder.repaint();
        }

        private class DetailTab extends JLabel {
            private boolean active;

            DetailTab(String text, int index) {
                super(text);
                setFont(spaced(sans(Font.PLAIN, 14), 0.012f));
                setBorder(new EmptyBorder(14, 0, 14, 36));
                setCursor(handCursor);
                setForeground(muted);
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        setTab(index);
                    }
                });
            }

            void setActive(boolean value) {
                active = value;
                setForeground(active ? green : muted);
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (active) {
                    g.setColor(green);
                    g.fillRect(0, getHeight() - 2, getWidth() - 36, 2);
                }
            }
        }

        // ---------- overview tab ----------

        private JPanel buildOverviewCard() {
            JPanel card = verticalPanel();
            card.setBorder(new EmptyBorder(28, 0, 10, 0));

            card.add(h2("About"));
            card.add(Box.createVerticalStrut(16));

            JPanel meta = rowPanel();
            addMeta(meta, IconKind.CALENDAR, "Release Date", formatDate(js(root, "release_date")));
            addMeta(meta, IconKind.CLOCK, "Runtime", formatRuntime(ji(root, "runtime")));
            addMeta(meta, IconKind.GLOBE, "Language", languageName());
            if (meta.getComponentCount() > 0) {
                JPanel metaWrap = new JPanel(new BorderLayout());
                metaWrap.setOpaque(false);
                metaWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
                metaWrap.add(meta, BorderLayout.WEST);
                card.add(metaWrap);
                card.add(Box.createVerticalStrut(34));
            }

            JsonObject credits = jo(root, "credits");
            JsonArray crew = ja(credits, "crew");
            JsonArray cast = ja(credits, "cast");

            JPanel people = new JPanel(new BorderLayout(40, 0));
            people.setOpaque(false);
            people.setAlignmentX(Component.LEFT_ALIGNMENT);

            // left: director + writers
            WidthPanel left = new WidthPanel(330);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setOpaque(false);
            List<JsonObject> directors = crewPeople(crew, Set.of("Director"), 2);
            if (!directors.isEmpty()) {
                left.add(h2("Director"));
                left.add(Box.createVerticalStrut(14));
                for (JsonObject d : directors) {
                    JPanel row = personRow(js(d, "name"), js(d, "job"), js(d, "profile_path"));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    left.add(row);
                    left.add(Box.createVerticalStrut(10));
                }
            }
            List<JsonObject> writers = crewPeople(crew, Set.of("Writer", "Screenplay", "Story"), 4);
            if (!writers.isEmpty()) {
                left.add(Box.createVerticalStrut(18));
                left.add(h2("Writers"));
                left.add(Box.createVerticalStrut(14));
                JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
                grid.setOpaque(false);
                grid.setAlignmentX(Component.LEFT_ALIGNMENT);
                for (JsonObject w : writers) {
                    grid.add(personRow(js(w, "name"), js(w, "job"), js(w, "profile_path")));
                }
                left.add(grid);
            }
            JPanel leftHolder = new JPanel(new BorderLayout());
            leftHolder.setOpaque(false);
            leftHolder.add(left, BorderLayout.NORTH);
            if (left.getComponentCount() > 0) {
                people.add(leftHolder, BorderLayout.WEST);
            }

            // right: cast
            if (cast.size() > 0) {
                JPanel castPanel = new JPanel(new BorderLayout(0, 14));
                castPanel.setOpaque(false);
                JPanel head = new JPanel(new BorderLayout());
                head.setOpaque(false);
                head.add(h2("Cast"), BorderLayout.WEST);
                head.add(linkLabel("See All  \u2192", () -> setTab(1)), BorderLayout.EAST);
                JPanel row = new JPanel(new RowLayout(100, 12));
                row.setOpaque(false);
                int n = Math.min(10, cast.size());
                for (int i = 0; i < n; i++) {
                    JsonObject c = cast.get(i).getAsJsonObject();
                    row.add(new CastCard(js(c, "name"), js(c, "character"), js(c, "profile_path")));
                }
                castPanel.add(head, BorderLayout.NORTH);
                castPanel.add(row, BorderLayout.CENTER);
                people.add(castPanel, BorderLayout.CENTER);
            }

            if (people.getComponentCount() > 0) {
                card.add(people);
            }
            return card;
        }

        private String languageName() {
            String code = js(root, "original_language");
            if (code.isEmpty()) {
                return "";
            }
            String name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH);
            return name == null || name.isBlank() ? code : name;
        }

        private void addMeta(JPanel row, IconKind icon, String label, String value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            if (row.getComponentCount() > 0) {
                row.add(Box.createHorizontalStrut(24));
                JPanel divider = new JPanel();
                divider.setBackground(borderColor);
                divider.setPreferredSize(new Dimension(1, 34));
                divider.setMaximumSize(new Dimension(1, 34));
                row.add(divider);
                row.add(Box.createHorizontalStrut(24));
            }
            JPanel group = new JPanel(new BorderLayout(12, 0));
            group.setOpaque(false);
            group.add(new JLabel(new VectorIcon(icon, 24, muted, false)), BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            JLabel l = new JLabel(label);
            l.setForeground(muted);
            l.setFont(sans(Font.PLAIN, 11));
            JLabel v = new JLabel(value);
            v.setForeground(new Color(222, 228, 232));
            v.setFont(sans(Font.PLAIN, 14));
            text.add(l);
            text.add(Box.createVerticalStrut(4));
            text.add(v);
            group.add(text, BorderLayout.CENTER);
            group.setMaximumSize(group.getPreferredSize());
            row.add(group);
        }

        private List<JsonObject> crewPeople(JsonArray crew, Set<String> jobs, int limit) {
            List<JsonObject> out = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (JsonElement e : crew) {
                JsonObject c = e.getAsJsonObject();
                if (jobs.contains(js(c, "job")) && seen.add(ji(c, "id"))) {
                    out.add(c);
                    if (out.size() >= limit) {
                        break;
                    }
                }
            }
            return out;
        }

        private JPanel personRow(String name, String role, String profilePath) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);

            JPanel avatarWrap = new JPanel(new GridBagLayout());
            avatarWrap.setOpaque(false);
            avatarWrap.add(new AvatarView(50, profilePath));

            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            JLabel n = new JLabel(name);
            n.setForeground(textColor);
            n.setFont(sans(Font.PLAIN, 13));
            JLabel r = new JLabel(role);
            r.setForeground(muted);
            r.setFont(sans(Font.PLAIN, 11));
            text.add(Box.createVerticalGlue());
            text.add(n);
            text.add(Box.createVerticalStrut(4));
            text.add(r);
            text.add(Box.createVerticalGlue());

            row.add(avatarWrap, BorderLayout.WEST);
            row.add(text, BorderLayout.CENTER);
            return row;
        }

        // ---------- cast & crew tab ----------

        private JPanel buildCastCard() {
            JPanel card = verticalPanel();
            card.setBorder(new EmptyBorder(28, 0, 10, 0));
            JsonObject credits = jo(root, "credits");
            JsonArray cast = ja(credits, "cast");
            if (cast.size() == 0) {
                card.add(createCenteredMessage("No cast information", "TMDB has no credits for this film."));
                return card;
            }

            card.add(h2("Cast"));
            card.add(Box.createVerticalStrut(16));
            JPanel grid = new JPanel(new FillGridLayout(130, 16, 22));
            grid.setOpaque(false);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            int n = Math.min(36, cast.size());
            for (int i = 0; i < n; i++) {
                JsonObject c = cast.get(i).getAsJsonObject();
                grid.add(new CastCard(js(c, "name"), js(c, "character"), js(c, "profile_path")));
            }
            card.add(grid);

            // key crew, one entry per person + job
            Set<String> keyJobs = Set.of("Director", "Screenplay", "Writer", "Story", "Producer",
                    "Director of Photography", "Original Music Composer", "Editor");
            JPanel crewGrid = new JPanel(new GridLayout(0, 3, 16, 14));
            crewGrid.setOpaque(false);
            crewGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
            Set<String> seen = new HashSet<>();
            for (JsonElement e : ja(credits, "crew")) {
                JsonObject c = e.getAsJsonObject();
                String job = js(c, "job");
                if (keyJobs.contains(job) && seen.add(ji(c, "id") + job) && crewGrid.getComponentCount() < 12) {
                    crewGrid.add(personRow(js(c, "name"), job, js(c, "profile_path")));
                }
            }
            if (crewGrid.getComponentCount() > 0) {
                card.add(Box.createVerticalStrut(30));
                card.add(h2("Crew"));
                card.add(Box.createVerticalStrut(16));
                card.add(crewGrid);
            }
            return card;
        }

        // ---------- reviews tab ----------

        private JPanel buildReviewsCard() {
            JPanel card = verticalPanel();
            card.setBorder(new EmptyBorder(28, 0, 10, 0));
            JsonArray reviews = ja(jo(root, "reviews"), "results");
            if (reviews.size() == 0) {
                card.add(createCenteredMessage("No reviews yet", "TMDB has no reviews for this film."));
                return card;
            }
            int n = Math.min(8, reviews.size());
            for (int i = 0; i < n; i++) {
                JsonObject r = reviews.get(i).getAsJsonObject();
                String content = js(r, "content").replaceAll("\\s+", " ").trim();
                if (content.length() > 420) {
                    content = content.substring(0, 420).trim() + "\u2026";
                }
                String author = js(r, "author");
                double rating = jd(jo(r, "author_details"), "rating");
                String date = formatDate(js(r, "created_at").length() >= 10
                        ? js(r, "created_at").substring(0, 10) : "");

                RoundedPanel rc = new RoundedPanel(cardColor, borderColor, 14);
                rc.setLayout(new BorderLayout(0, 8));
                rc.setBorder(new EmptyBorder(14, 16, 14, 16));
                rc.setAlignmentX(Component.LEFT_ALIGNMENT);

                JPanel head = rowPanel();
                JLabel a = new JLabel(author.isEmpty() ? "Anonymous" : author);
                a.setForeground(textColor);
                a.setFont(sans(Font.BOLD, 13));
                head.add(a);
                if (rating > 0) {
                    JLabel s = new JLabel(String.format(Locale.US, "%.1f", rating),
                            new VectorIcon(IconKind.STAR, 12, green, true), SwingConstants.LEFT);
                    s.setIconTextGap(5);
                    s.setForeground(green);
                    s.setFont(sans(Font.BOLD, 12));
                    head.add(Box.createHorizontalStrut(12));
                    head.add(s);
                }
                if (!date.isEmpty()) {
                    JLabel d = new JLabel(date);
                    d.setForeground(muted);
                    d.setFont(sans(Font.PLAIN, 11));
                    head.add(Box.createHorizontalStrut(12));
                    head.add(d);
                }
                TextBlock body = new TextBlock(5, 1.4f);
                body.setTextFont(sans(Font.PLAIN, 13), 0.01f);
                body.setForeground(new Color(204, 208, 213));
                body.setBlockWidth(680);
                body.setText(content);

                rc.add(head, BorderLayout.NORTH);
                rc.add(body, BorderLayout.CENTER);
                rc.setMaximumSize(new Dimension(Integer.MAX_VALUE, rc.getPreferredSize().height));
                card.add(rc);
                card.add(Box.createVerticalStrut(12));
            }
            return card;
        }

        // ---------- similar tab ----------

        private JPanel buildSimilarCard() {
            JPanel card = verticalPanel();
            card.setBorder(new EmptyBorder(28, 0, 10, 0));
            if (similar.isEmpty()) {
                card.add(createCenteredMessage("Nothing similar found", "Try another film."));
                return card;
            }
            card.add(h2(similarTitle));
            card.add(Box.createVerticalStrut(16));
            JPanel grid = new JPanel(new FillGridLayout(130, 18, 26));
            grid.setOpaque(false);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (TmdbMovie m : similar) {
                grid.add(new MovieTile(m, 130));
            }
            card.add(grid);
            return card;
        }

        // ---------- right column ----------

        private void buildSide() {
            JPanel side = verticalPanel();

            JPanel simBody = verticalPanel();
            int n = Math.min(4, similar.size());
            for (int i = 0; i < n; i++) {
                simBody.add(sideRow(similar.get(i), 0, 44, 64, true));
            }
            if (n > 0) {
                side.add(sideCard(similarTitle, () -> setTab(3), simBody));
                side.add(Box.createVerticalStrut(18));
            }

            JPanel trendBody = verticalPanel();
            int rank = 0;
            for (TmdbMovie m : data.trending) {
                if (rank >= 5) {
                    break;
                }
                trendBody.add(sideRow(m, ++rank, 36, 52, false));
            }
            if (rank > 0) {
                side.add(sideCard("Trending on TMDB", null, trendBody));
            }

            sideHolder.setOpaque(false);
            sideHolder.setLayout(new BorderLayout());
            sideHolder.setBorder(new EmptyBorder(257, 0, 0, 0));
            sideHolder.add(side, BorderLayout.NORTH);
        }

        private JPanel sideCard(String heading, Runnable seeAll, JPanel body) {
            RoundedPanel p = new RoundedPanel(glass, borderColor, 12);
            p.setLayout(new BorderLayout(0, 8));
            p.setBorder(new EmptyBorder(14, 14, 12, 14));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            JLabel h = new JLabel(heading);
            h.setForeground(textColor);
            h.setFont(spaced(serif(Font.BOLD, 16), -0.004f));
            head.add(h, BorderLayout.WEST);
            if (seeAll != null) {
                head.add(linkLabel("See All  \u2192", seeAll), BorderLayout.EAST);
            }
            p.add(head, BorderLayout.NORTH);
            p.add(body, BorderLayout.CENTER);
            return p;
        }

        private JPanel sideRow(TmdbMovie m, int rank, int pw, int ph, boolean showYear) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setBorder(new EmptyBorder(5, 0, 5, 0));
            row.setCursor(handCursor);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ph + 10));

            JPanel west = new JPanel(new BorderLayout(10, 0));
            west.setOpaque(false);
            if (rank > 0) {
                JLabel r = new JLabel(String.valueOf(rank), SwingConstants.CENTER);
                r.setForeground(textColor);
                r.setFont(sans(Font.PLAIN, 12));
                r.setPreferredSize(new Dimension(14, 10));
                west.add(r, BorderLayout.WEST);
            }
            PosterView p = new PosterView(elevatedColor, 5);
            p.setPreferredSize(new Dimension(pw, ph));
            p.setTextStyle(sans(Font.PLAIN, 8), muted);
            String path = m.getPoster_path();
            if (path != null && !path.isBlank()) {
                loadPosterView(p, imageBase + path);
            }
            west.add(p, BorderLayout.CENTER);

            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            JLabel t = new JLabel(safeTitle(m.getTitle()));
            t.setForeground(textColor);
            t.setFont(spaced(sans(Font.PLAIN, 12), 0.01f));
            text.add(Box.createVerticalGlue());
            text.add(t);
            if (showYear) {
                JLabel y = new JLabel(getYear(m.getRelease_date()));
                y.setForeground(muted);
                y.setFont(sans(Font.PLAIN, 11));
                text.add(Box.createVerticalStrut(3));
                text.add(y);
            }
            double vote = m.getVote_average();
            JLabel s = new JLabel(vote > 0 ? String.format(Locale.US, "%.1f", vote) : "NR",
                    new VectorIcon(IconKind.STAR, 11, green, true), SwingConstants.LEFT);
            s.setIconTextGap(5);
            s.setForeground(green);
            s.setFont(sans(Font.BOLD, 11));
            text.add(Box.createVerticalStrut(3));
            text.add(s);
            text.add(Box.createVerticalGlue());

            row.add(west, BorderLayout.WEST);
            row.add(text, BorderLayout.CENTER);
            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showMovieDetails(m);
                }
            });
            return row;
        }
    }

    private void openTrailer(TmdbMovie movie) {
        try {
            int year = parseYear(movie.getRelease_date());
            String query = safeTitle(movie.getTitle())
                    + (year == 0 ? "" : " " + year) + " official trailer";
            URI uri = URI.create("https://www.youtube.com/results?search_query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8));

            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Could not open a browser on this system.",
                        "Trailer", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            showError("Could not open the trailer.", e);
        }
    }

    // ---------------------------------------------------------
    // LIBRARY STORAGE
    // ---------------------------------------------------------

    private void addMovieToLibrary(TmdbMovie tmdbMovie) {
        String title = safeTitle(tmdbMovie.getTitle());
        int year = parseYear(tmdbMovie.getRelease_date());

        for (Movie movie : movies) {
            if (movie.getTitle() != null
                    && movie.getTitle().equalsIgnoreCase(title)
                    && movie.getReleaseYear() == year) {

                JOptionPane.showMessageDialog(this,
                        "This movie is already in your library.",
                        "Already added", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        Movie movie = new Movie(title, year, "Unknown");
        movie.setWatched(false);
        movie.setRating(0.0);
        movie.setPosterPath(tmdbMovie.getPoster_path());

        movies.add(movie);
        saveMovies();

        JOptionPane.showMessageDialog(this,
                title + " was added to your library.",
                "Movie added", JOptionPane.INFORMATION_MESSAGE);

        setStatus("Added " + title + " to your library.");
    }

    private void saveMovies() {
        try {
            MovieStorage.saveMovies(movies);
            setStatus("Library saved.");
        } catch (Exception e) {
            showError("Could not save your library.", e);
        }
    }

    // ---------------------------------------------------------
    // SAVED MOVIES
    // ---------------------------------------------------------

    private void showSavedPage(String title, String subtitle, Predicate<Movie> filter) {
        currentPage = title;
        navToken++;
        currentSavedView = () -> showSavedPage(title, subtitle, filter);

        JPanel heading = createPage(title, subtitle);

        int gap = 16;
        ScrollPanel grid = new ScrollPanel(new WrapLayout(FlowLayout.LEFT, gap, gap));
        grid.setBackground(background);
        grid.setBorder(new EmptyBorder(5, -gap, 20, 0));

        showPageContent(heading, createScroll(grid));

        ArrayList<Movie> filtered = new ArrayList<>();
        for (Movie movie : movies) {
            if (filter == null || filter.test(movie)) {
                filtered.add(movie);
            }
        }

        if (filtered.isEmpty()) {
            grid.add(createCenteredMessage("Nothing here yet",
                    "Explore movies and add films to build your diary."));
        } else {
            for (Movie movie : filtered) {
                grid.add(createSavedMovieCard(movie));
            }
        }

        setStatus(filtered.size() + " saved movies.");
    }

    private JPanel createSavedMovieCard(Movie movie) {
        RoundedPanel card = new RoundedPanel(cardColor, borderColor, 18);
        card.setLayout(new BorderLayout(18, 0));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.setPreferredSize(new Dimension(450, 254));

        PosterView poster = new PosterView(elevatedColor, 12);
        poster.setTextStyle(sans(Font.PLAIN, 10), muted);
        poster.setPreferredSize(new Dimension(126, 224));

        if (movie.getPosterPath() != null && !movie.getPosterPath().isBlank()) {
            loadPosterView(poster, imageBase + movie.getPosterPath());
        } else {
            poster.setMessage("No poster");
        }

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));

        TextBlock title = new TextBlock(2, 1.15f);
        title.setTextFont(sans(Font.BOLD, 15), 0.01f);
        title.setForeground(textColor);
        title.setBlockWidth(230);
        title.setText(safeTitle(movie.getTitle()));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel year = new JLabel(String.valueOf(movie.getReleaseYear()));
        year.setForeground(muted);
        year.setFont(spaced(sans(Font.PLAIN, 12), 0.02f));
        year.setBorder(new EmptyBorder(0, 0, 0, 4));
        year.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel watched = new JLabel(movie.isWatched() ? "Watched" : "In watchlist");
        watched.setForeground(green);
        watched.setFont(spaced(sans(Font.BOLD, 11), 0.04f));
        watched.setBorder(new EmptyBorder(0, 0, 0, 4));
        watched.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel rating = new JLabel("Your rating: "
                + (movie.getRating() > 0 ? movie.getRating() + "/10" : "Not rated"));
        rating.setForeground(muted);
        rating.setFont(spaced(sans(Font.PLAIN, 11), 0.02f));
        rating.setBorder(new EmptyBorder(0, 0, 0, 4));
        rating.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        
if ("Watchlist".equals(currentPage)) {
    JButton toggle = new JButton("Watched");
    stylePill(toggle, green, darkText, null);
    toggle.setBorder(new EmptyBorder(7, 14, 7, 14));

    toggle.addActionListener(e -> {
        movie.setWatched(true);
        saveMovies();
        refreshSavedView();
    });

    buttons.add(toggle);
}

        

        JButton rate = new JButton("Rate");
        stylePill(rate, elevatedColor, textColor, null);
        rate.setBorder(new EmptyBorder(7, 14, 7, 14));
        rate.addActionListener(e -> {
            String value = JOptionPane.showInputDialog(this,
                    "Enter your rating from 1 to 10:",
                    "Rate Movie", JOptionPane.QUESTION_MESSAGE);

            if (value == null) {
                return;
            }

            try {
                double score = Double.parseDouble(value.trim());
                if (score < 1 || score > 10) {
                    throw new NumberFormatException();
                }
                movie.setRating(score);
                saveMovies();
                refreshSavedView();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter a number between 1 and 10.");
            }
        });

        JButton remove = new JButton("Remove");
        stylePill(remove, new Color(116, 48, 53), Color.WHITE, null);
        remove.setBorder(new EmptyBorder(7, 14, 7, 14));
        remove.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Remove " + movie.getTitle() + " from your library?",
                    "Confirm Removal", JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.YES_OPTION) {
                movies.remove(movie);
                saveMovies();
                refreshSavedView();
            }
        });

        buttons.add(rate);
        buttons.add(remove);

        if ("Watchlist".equals(currentSavedView)) {
    JButton toggle = new JButton("Watched");
    stylePill(toggle, green, darkText, null);
    toggle.setBorder(new EmptyBorder(7, 14, 7, 14));

    toggle.addActionListener(e -> {
        movie.setWatched(true);
        saveMovies();
        refreshSavedView();
    });

    buttons.add(toggle);
}

        details.add(title);
        details.add(Box.createVerticalStrut(8));
        details.add(year);
        details.add(Box.createVerticalStrut(12));
        details.add(watched);
        details.add(Box.createVerticalStrut(8));
        details.add(rating);
        details.add(Box.createVerticalStrut(16));
        details.add(buttons);

        card.add(poster, BorderLayout.WEST);
        card.add(details, BorderLayout.CENTER);
        return card;
    }

    private void refreshSavedView() {
        if (currentSavedView != null) {
            currentSavedView.run();
        }
    }

    // ---------------------------------------------------------
    // IMAGE LOADING
    // ---------------------------------------------------------

    private BufferedImage fetchImage(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<byte[]> response = imageClient.send(
                request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            return null;
        }
        return ImageIO.read(new ByteArrayInputStream(response.body()));
    }

    private static BufferedImage coverScale(BufferedImage image, int width,
                                            int height, int arc, double focusY) {
        double targetRatio = (double) width / height;
        double sourceRatio = (double) image.getWidth() / image.getHeight();

        int cropWidth = image.getWidth();
        int cropHeight = image.getHeight();

        if (sourceRatio > targetRatio) {
            cropWidth = Math.max(1, (int) (image.getHeight() * targetRatio));
        } else {
            cropHeight = Math.max(1, (int) (image.getWidth() / targetRatio));
        }

        int cropX = (image.getWidth() - cropWidth) / 2;
        int cropY = (int) ((image.getHeight() - cropHeight) * focusY);

        BufferedImage cropped = image.getSubimage(cropX, cropY, cropWidth, cropHeight);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        if (arc > 0) {
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Float(0, 0, width, height, arc, arc));
            g2.setComposite(AlphaComposite.SrcIn);
        }

        g2.drawImage(cropped, 0, 0, width, height, null);
        g2.dispose();
        return out;
    }

    /** Loads a poster into a PosterView; the view crops it to fill its 9:16 box. */
    private void loadPosterView(PosterView view, String url) {
        BufferedImage cached = rawCache.get(url);
        if (cached != null) {
            view.setSource(cached);
            return;
        }

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return fetchImage(url);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage image = get();
                    if (image != null) {
                        rawCache.put(url, image);
                        view.setSource(image);
                    } else {
                        view.setMessage("Poster unavailable");
                    }
                } catch (Exception e) {
                    view.setMessage("Poster unavailable");
                }
            }
        }.execute();
    }

    /** Icon-based loader (used for the details backdrop). */
    private void loadPoster(JLabel label, String url, int width, int height, int arc) {
        String cacheKey = url + "_" + width + "_" + height + "_" + arc;
        ImageIcon cached = posterCache.get(cacheKey);

        if (cached != null) {
            label.setIcon(cached);
            label.setText("");
            return;
        }

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                BufferedImage image = fetchImage(url);
                if (image == null) {
                    return null;
                }
                return new ImageIcon(coverScale(image, width, height, arc, 0.5));
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();

                    if (icon != null) {
                        posterCache.put(cacheKey, icon);
                        label.setIcon(icon);
                        label.setText("");
                    } else {
                        label.setIcon(null);
                    }
                } catch (Exception e) {
                    label.setIcon(null);
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------
    // UTILITIES
    // ---------------------------------------------------------

    private String safeTitle(String title) {
        return title == null || title.isBlank() ? "Untitled" : title;
    }

    private int parseYear(String releaseDate) {
        try {
            if (releaseDate != null && releaseDate.length() >= 4) {
                return Integer.parseInt(releaseDate.substring(0, 4));
            }
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    private String getYear(String releaseDate) {
        int year = parseYear(releaseDate);
        return year == 0 ? "Year unknown" : String.valueOf(year);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message, Exception exception) {
        exception.printStackTrace();

        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = exception.getClass().getSimpleName();
        }

        JOptionPane.showMessageDialog(this,
                message + "\n\n" + detail,
                "AbsoluteCinema", JOptionPane.ERROR_MESSAGE);
    }
    


    // ---------------------------------------------------------
    // CUSTOM COMPONENTS
    // ---------------------------------------------------------


    /** Wrapped text with real control over letter spacing and line spacing. */
    private static class TextBlock extends JComponent {
        private static final FontRenderContext FRC =
                new FontRenderContext(null, true, true);

        private final int maxLines;
        private final float lineSpacing;
        private final List<TextLayout> lines = new ArrayList<>();
        private final List<Float> advances = new ArrayList<>();

        private String text = "";
        private Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        private float tracking = 0f;
        private int blockWidth = -1;
        private int laidWidth = -1;
        private int totalHeight = 0;

        TextBlock(int maxLines, float lineSpacing) {
            this.maxLines = maxLines;
            this.lineSpacing = lineSpacing;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        void setText(String value) {
            text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
            laidWidth = -1;
            revalidate();
            repaint();
        }

        void setTextFont(Font f, float trackingValue) {
            font = f;
            tracking = trackingValue;
            laidWidth = -1;
            revalidate();
            repaint();
        }

        void setBlockWidth(int width) {
            blockWidth = width;
            laidWidth = -1;
            revalidate();
        }

        int heightFor(int width) {
            layoutLines(width);
            return totalHeight;
        }

        private TextLayout makeLayout(String s) {
            AttributedString as = new AttributedString(s);
            as.addAttribute(TextAttribute.FONT, font);
            if (tracking != 0f) {
                as.addAttribute(TextAttribute.TRACKING, tracking);
            }
            return new TextLayout(as.getIterator(), FRC);
        }

        private void layoutLines(int width) {
            if (width == laidWidth) {
                return;
            }
            laidWidth = width;
            lines.clear();
            advances.clear();
            totalHeight = 0;

            if (width <= 0 || text.isEmpty()) {
                return;
            }

            AttributedString as = new AttributedString(text);
            as.addAttribute(TextAttribute.FONT, font);
            if (tracking != 0f) {
                as.addAttribute(TextAttribute.TRACKING, tracking);
            }

            LineBreakMeasurer measurer = new LineBreakMeasurer(as.getIterator(), FRC);
            float sum = 0f;

            while (measurer.getPosition() < text.length() && lines.size() < maxLines) {
                int start = measurer.getPosition();
                boolean last = lines.size() == maxLines - 1;
                TextLayout layout = measurer.nextLayout(width);
                int end = measurer.getPosition();

                if (last && end < text.length()) {
                    String s = text.substring(start, end).trim();
                    layout = makeLayout(s + "\u2026");
                    while (layout.getAdvance() > width && s.length() > 1) {
                        s = s.substring(0, s.length() - 1).trim();
                        layout = makeLayout(s + "\u2026");
                    }
                }

                float natural = layout.getAscent() + layout.getDescent()
                        + layout.getLeading();
                float advance = natural * lineSpacing;
                lines.add(layout);
                advances.add(advance);
                sum += advance;
            }

            totalHeight = (int) Math.ceil(sum);
        }

        @Override
        public Dimension getPreferredSize() {
            if (blockWidth > 0) {
                return new Dimension(blockWidth, heightFor(blockWidth));
            }
            return super.getPreferredSize();
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return blockWidth > 0 ? getPreferredSize() : super.getMaximumSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            layoutLines(getWidth());
            if (lines.isEmpty()) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setColor(getForeground());

            float y = 0f;
            for (int i = 0; i < lines.size(); i++) {
                TextLayout layout = lines.get(i);
                float advance = advances.get(i);
                float natural = layout.getAscent() + layout.getDescent()
                        + layout.getLeading();
                float baseline = y + (advance - natural) / 2f + layout.getAscent();
                layout.draw(g2, 0, baseline);
                y += advance;
            }
            g2.dispose();
        }
    }

    /** Poster that always fills its box (cover-crop) with rounded corners. */
    private static class PosterView extends JComponent {
        private final Color fill;
        private final int arc;
        private BufferedImage source;
        private BufferedImage cache;
        private int cacheW;
        private int cacheH;
        private String message = "";
        private Font messageFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
        private Color messageColor = Color.GRAY;

        PosterView(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        void setSource(BufferedImage image) {
            source = image;
            cache = null;
            message = "";
            repaint();
        }

        void setMessage(String value) {
            message = value;
            repaint();
        }

        void setTextStyle(Font font, Color color) {
            messageFont = font;
            messageColor = color;
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            if (source != null) {
                if (cache == null || cacheW != w || cacheH != h) {
                    cache = coverScale(source, w, h, arc, 0.5);
                    cacheW = w;
                    cacheH = h;
                }
                g2.drawImage(cache, 0, 0, null);
            } else {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                if (!message.isEmpty()) {
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(messageFont);
                    g2.setColor(messageColor);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(message,
                            (w - fm.stringWidth(message)) / 2,
                            (h + fm.getAscent() - fm.getDescent()) / 2);
                }
            }
            g2.dispose();
        }
    }

    /** Panel with an antialiased rounded fill and border. */
    private static class RoundedPanel extends JPanel {
        private final Color fill;
        private final Color line;
        private final int arc;

        RoundedPanel(Color fill, Color line, int arc) {
            this.fill = fill;
            this.line = line;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            if (line != null) {
                g2.setColor(line);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Vertically scrolling panel that always matches the viewport width. */
    private static class ScrollPanel extends JPanel implements Scrollable {
        ScrollPanel(LayoutManager layout) {
            super(layout);
            addComponentListener(new ComponentAdapter() {
                private int last = -1;

                @Override
                public void componentResized(ComponentEvent e) {
                    if (getWidth() != last) {
                        last = getWidth();
                        invalidateDeep(ScrollPanel.this);
                        revalidate();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return Math.max(40, r.height - 40);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Thin dark scrollbar. */
    private static class DarkScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(44, 57, 66);
            trackColor = new Color(10, 14, 18);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return emptyButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return emptyButton();
        }

        private JButton emptyButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            return b;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }

    // ---------------------------------------------------------
    // TILE LAYOUTS (columns stretch so posters always fill the width)
    // ---------------------------------------------------------

    private static int availableWidth(Container parent) {
        Insets in = parent.getInsets();
        int w = parent.getWidth();
        if (w <= 0) {
            Container viewport = SwingUtilities.getAncestorOfClass(JViewport.class, parent);
            w = viewport != null && viewport.getWidth() > 0 ? viewport.getWidth() : 900;
        }
        return Math.max(1, w - in.left - in.right);
    }

    private static int tileHeight(Component c, int width) {
        if (c instanceof HeightForWidth) {
            return ((HeightForWidth) c).heightForWidth(width);
        }
        return c.getPreferredSize().height;
    }

    /** One horizontal row; tile width stretches so the row is exactly full. */
    private static class RowLayout implements LayoutManager {
        private final int minTileW;
        private final int gap;

        RowLayout(int minTileW, int gap) {
            this.minTileW = minTileW;
            this.gap = gap;
        }

        private int columns(int avail) {
            return Math.max(1, (avail + gap) / (minTileW + gap));
        }

        private int tileWidth(int avail, int cols) {
            return cols > 1 ? (avail - (cols - 1) * gap) / cols : avail;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            int avail = availableWidth(parent);
            int cols = columns(avail);
            int tileW = tileWidth(avail, cols);
            int shown = Math.min(cols, parent.getComponentCount());

            int h = 0;
            for (int i = 0; i < shown; i++) {
                h = Math.max(h, tileHeight(parent.getComponent(i), tileW));
            }
            return new Dimension(avail + in.left + in.right, h + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            int n = parent.getComponentCount();
            if (n == 0) {
                return;
            }

            Insets in = parent.getInsets();
            int avail = availableWidth(parent);
            int cols = columns(avail);
            int tileW = tileWidth(avail, cols);
            int h = parent.getHeight() - in.top - in.bottom;

            int x = in.left;
            for (int i = 0; i < n; i++) {
                Component c = parent.getComponent(i);
                if (i < cols) {
                    c.setBounds(x, in.top, tileW, h);
                    x += tileW + gap;
                } else {
                    c.setBounds(0, 0, 0, 0);
                }
            }
        }
    }

    /** Multi-row grid with the same fill behaviour. */
    private static class FillGridLayout implements LayoutManager {
        private final int minTileW;
        private final int hgap;
        private final int vgap;

        FillGridLayout(int minTileW, int hgap, int vgap) {
            this.minTileW = minTileW;
            this.hgap = hgap;
            this.vgap = vgap;
        }

        private int columns(int avail) {
            return Math.max(1, (avail + hgap) / (minTileW + hgap));
        }

        private int tileWidth(int avail, int cols) {
            return cols > 1 ? (avail - (cols - 1) * hgap) / cols : avail;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            int avail = availableWidth(parent);
            int cols = columns(avail);
            int tileW = tileWidth(avail, cols);
            int n = parent.getComponentCount();

            int total = 0;
            for (int start = 0; start < n; start += cols) {
                int rowH = 0;
                for (int i = start; i < Math.min(n, start + cols); i++) {
                    rowH = Math.max(rowH, tileHeight(parent.getComponent(i), tileW));
                }
                total += rowH + (start > 0 ? vgap : 0);
            }
            return new Dimension(avail + in.left + in.right, total + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int avail = availableWidth(parent);
            int cols = columns(avail);
            int tileW = tileWidth(avail, cols);
            int n = parent.getComponentCount();

            int y = in.top;
            for (int start = 0; start < n; start += cols) {
                int rowH = 0;
                for (int i = start; i < Math.min(n, start + cols); i++) {
                    rowH = Math.max(rowH, tileHeight(parent.getComponent(i), tileW));
                }
                for (int i = start; i < Math.min(n, start + cols); i++) {
                    int col = i - start;
                    parent.getComponent(i).setBounds(
                            in.left + col * (tileW + hgap), y, tileW, rowH);
                }
                y += rowH + vgap;
            }
        }
    }

    // ---------------------------------------------------------
    // VECTOR ICONS (drawn in code so they look the same everywhere)
    // ---------------------------------------------------------

    private enum IconKind {
        HOME, COMPASS, FLAME, STAR, CALENDAR, GRID, BOOKMARK, HEART,
        CHAT, SEARCH, USER, FILM, PLAY, CHEVRON, MENU,
        PLUS, MORE, CHECK, CLOCK, GLOBE
    }

    private static class VectorIcon implements Icon {
        private static final Color FILM_DARK = new Color(10, 14, 18);

        private final IconKind kind;
        private final int size;
        private final Color color;
        private final boolean filled;

        VectorIcon(IconKind kind, int size, Color color, boolean filled) {
            this.kind = kind;
            this.size = size;
            this.color = color;
            this.filled = filled;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        private void paintShape(Graphics2D g2, Shape s) {
            if (filled) {
                g2.fill(s);
            } else {
                g2.draw(s);
            }
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            double s = size / 24.0;
            g2.scale(s, s);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (kind) {
                case HOME: {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(3, 11);
                    p.lineTo(12, 3);
                    p.lineTo(21, 11);
                    p.lineTo(21, 20);
                    p.lineTo(14.5, 20);
                    p.lineTo(14.5, 14);
                    p.lineTo(9.5, 14);
                    p.lineTo(9.5, 20);
                    p.lineTo(3, 20);
                    p.closePath();
                    paintShape(g2, p);
                    break;
                }
                case COMPASS: {
                    g2.draw(new Ellipse2D.Double(3, 3, 18, 18));
                    Path2D.Double n = new Path2D.Double();
                    n.moveTo(15.5, 8.5);
                    n.lineTo(13.5, 13.5);
                    n.lineTo(8.5, 15.5);
                    n.lineTo(10.5, 10.5);
                    n.closePath();
                    paintShape(g2, n);
                    break;
                }
                case FLAME: {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(12, 3);
                    p.curveTo(12, 3, 5.5, 8.5, 5.5, 14.5);
                    p.curveTo(5.5, 18.5, 8.4, 21, 12, 21);
                    p.curveTo(15.6, 21, 18.5, 18.5, 18.5, 14.5);
                    p.curveTo(18.5, 11.5, 17, 9.5, 15.5, 8);
                    p.curveTo(15, 10, 14, 11, 13, 11);
                    p.curveTo(13.6, 8, 13, 5.5, 12, 3);
                    p.closePath();
                    paintShape(g2, p);
                    break;
                }
                case STAR: {
                    Path2D.Double p = new Path2D.Double();
                    for (int i = 0; i < 10; i++) {
                        double angle = -Math.PI / 2 + i * Math.PI / 5;
                        double r = (i % 2 == 0) ? 10 : 4.3;
                        double px = 12 + r * Math.cos(angle);
                        double py = 12.8 + r * Math.sin(angle);
                        if (i == 0) {
                            p.moveTo(px, py);
                        } else {
                            p.lineTo(px, py);
                        }
                    }
                    p.closePath();
                    paintShape(g2, p);
                    break;
                }
                case CALENDAR: {
                    g2.draw(new RoundRectangle2D.Double(3.5, 5, 17, 15.5, 3, 3));
                    g2.draw(new Line2D.Double(3.5, 10, 20.5, 10));
                    g2.draw(new Line2D.Double(8, 3, 8, 7));
                    g2.draw(new Line2D.Double(16, 3, 16, 7));
                    break;
                }
                case GRID: {
                    g2.fill(new RoundRectangle2D.Double(4, 4, 7, 7, 2, 2));
                    g2.fill(new RoundRectangle2D.Double(13, 4, 7, 7, 2, 2));
                    g2.fill(new RoundRectangle2D.Double(4, 13, 7, 7, 2, 2));
                    g2.fill(new RoundRectangle2D.Double(13, 13, 7, 7, 2, 2));
                    break;
                }
                case BOOKMARK: {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(6, 3.5);
                    p.lineTo(18, 3.5);
                    p.lineTo(18, 21);
                    p.lineTo(12, 16.5);
                    p.lineTo(6, 21);
                    p.closePath();
                    paintShape(g2, p);
                    break;
                }
                case HEART: {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(12, 20.5);
                    p.curveTo(4, 14, 3, 10, 3, 8);
                    p.curveTo(3, 5, 5.2, 3.5, 7.5, 3.5);
                    p.curveTo(9.5, 3.5, 11, 4.8, 12, 6.5);
                    p.curveTo(13, 4.8, 14.5, 3.5, 16.5, 3.5);
                    p.curveTo(18.8, 3.5, 21, 5, 21, 8);
                    p.curveTo(21, 10, 20, 14, 12, 20.5);
                    p.closePath();
                    paintShape(g2, p);
                    break;
                }

                
case CHECK: {
    Path2D.Double p = new Path2D.Double();
    p.moveTo(4, 12);
    p.lineTo(9, 17);
    p.lineTo(20, 6);
    g2.draw(p);
    break;
}

                case CHAT: {
                    g2.draw(new RoundRectangle2D.Double(3.5, 4, 17, 13, 3, 3));
                    Path2D.Double tail = new Path2D.Double();
                    tail.moveTo(8.5, 17);
                    tail.lineTo(8.5, 21);
                    tail.lineTo(12.5, 17);
                    g2.draw(tail);
                    g2.draw(new Line2D.Double(8, 9, 16, 9));
                    g2.draw(new Line2D.Double(8, 12.5, 13.5, 12.5));
                    break;
                }
                case SEARCH: {
                    g2.draw(new Ellipse2D.Double(3.5, 3.5, 12, 12));
                    g2.draw(new Line2D.Double(13.8, 13.8, 20, 20));
                    break;
                }
                case USER: {
                    paintShape(g2, new Ellipse2D.Double(8, 4, 8, 8));
                    Path2D.Double body = new Path2D.Double();
                    body.moveTo(4, 21);
                    body.curveTo(4, 16.5, 7.5, 14.5, 12, 14.5);
                    body.curveTo(16.5, 14.5, 20, 16.5, 20, 21);
                    paintShape(g2, body);
                    break;
                }
                case FILM: {
                    g2.fill(new RoundRectangle2D.Double(2, 3, 20, 18, 5, 5));
                    g2.setColor(FILM_DARK);
                    g2.fill(new RoundRectangle2D.Double(7.5, 6, 9, 12, 2, 2));
                    for (double hy : new double[]{6.2, 10.8, 15.4}) {
                        g2.fill(new Rectangle2D.Double(3.6, hy, 2.2, 2.4));
                        g2.fill(new Rectangle2D.Double(18.2, hy, 2.2, 2.4));
                    }
                    break;
                }
                case PLAY: {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(7, 4.5);
                    p.lineTo(20, 12);
                    p.lineTo(7, 19.5);
                    p.closePath();
                    g2.fill(p);
                    g2.draw(p);
                    break;
                }
                case CHEVRON: {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(6, 9);
                    p.lineTo(12, 15);
                    p.lineTo(18, 9);
                    g2.draw(p);
                    break;
                }
                case MENU: {
                    g2.draw(new Line2D.Double(4, 7, 20, 7));
                    g2.draw(new Line2D.Double(4, 12, 20, 12));
                    g2.draw(new Line2D.Double(4, 17, 14, 17));
                    break;
                }
                default:
                    break;
            }

            g2.dispose();
        }
    }

    // ---------------------------------------------------------
    // WRAPPING LAYOUT (genre chips, saved cards)
    // ---------------------------------------------------------

    private static class WrapLayout extends FlowLayout {

        WrapLayout(int alignment, int horizontalGap, int verticalGap) {
            super(alignment, horizontalGap, verticalGap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension size = layoutSize(target, false);
            size.width -= getHgap() + 1;
            return size;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;

                Container parent = target.getParent();
                if (parent instanceof JViewport) {
                    targetWidth = parent.getSize().width;
                } else if (targetWidth == 0) {
                    Container viewport =
                            SwingUtilities.getAncestorOfClass(JViewport.class, target);
                    targetWidth = viewport != null && viewport.getWidth() > 0
                            ? viewport.getWidth() : Integer.MAX_VALUE;
                }

                Insets insets = target.getInsets();
                int horizontalInsets = insets.left + insets.right + getHgap() * 2;

                int maxWidth = targetWidth - horizontalInsets;
                if (maxWidth <= 0) {
                    maxWidth = Integer.MAX_VALUE;
                }

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;

                int count = target.getComponentCount();

                for (int i = 0; i < count; i++) {
                    Component component = target.getComponent(i);

                    if (!component.isVisible()) {
                        continue;
                    }

                    Dimension d = preferred
                            ? component.getPreferredSize()
                            : component.getMinimumSize();

                    if (rowWidth > 0 && rowWidth + getHgap() + d.width > maxWidth) {
                        dim.width = Math.max(dim.width, rowWidth);
                        dim.height += rowHeight + getVgap();
                        rowWidth = 0;
                        rowHeight = 0;
                    }

                    if (rowWidth > 0) {
                        rowWidth += getHgap();
                    }

                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }

                dim.width = Math.max(dim.width, rowWidth);
                dim.height += rowHeight;

                dim.width += horizontalInsets;
                dim.height += insets.top + insets.bottom + getVgap() * 2;

                return dim;
            }
        }
    }

    // ---------------------------------------------------------
    // MAIN
    // ---------------------------------------------------------

    public static void main(String[] args) {
        System.setProperty("swing.aatext", "true");
        System.setProperty("awt.useSystemAAFontSettings", "on");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            Color dialogBg = new Color(15, 21, 26);
            Color dialogFg = new Color(240, 243, 245);
            UIManager.put("OptionPane.background", dialogBg);
            UIManager.put("Panel.background", dialogBg);
            UIManager.put("OptionPane.messageForeground", dialogFg);

            new AbsoluteCinemaGUI().setVisible(true);
        });
    }
}