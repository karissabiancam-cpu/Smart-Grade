import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.*;
import java.util.List;

// ============================================================
// 1. OOP: ABSTRACTION & INHERITANCE
// ============================================================
abstract class Person {
    protected String name, id;
    public Person(String name, String id) { this.name = name; this.id = id; }
    public abstract String getRole(); // Polymorphism
}

class Student extends Person {
    public Student(String name, String id) { super(name, id); }
    @Override public String getRole() { return "Student: " + name + " (" + id + ")"; }
}

// ============================================================
// 2. OOP: ENCAPSULATION
// ============================================================
class CourseRecord {
    private String code, name;
    private double grade;
    private int units;
    public CourseRecord(String code, String name, double grade, int units) {
        this.code = code; this.name = name; this.grade = grade; this.units = units;
    }
    public String getCode()  { return code; }
    public String getName()  { return name; }
    public double getGrade() { return grade; }
    public int    getUnits() { return units; }
}

// ============================================================
// DSA: Priority Queue Item with Comparable
// ============================================================
class Appeal implements Comparable<Appeal> {
    String student, course;
    boolean isSenior;
    public Appeal(String student, String course, boolean isSenior) {
        this.student = student; this.course = course; this.isSenior = isSenior;
    }
    @Override public int compareTo(Appeal o) { return Boolean.compare(o.isSenior, this.isSenior); }
}

// ============================================================
// 3. MAIN APP (Swing GUI + MySQL via JDBC)
// ============================================================
public class SmartGradeApp extends JFrame {

    // --- MySQL Configuration (matches XAMPP defaults) ---
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/smartgrade_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = ""; // XAMPP default: empty password

    // --- In-memory structures (kept for sorting / DSA demo) ---
    private final List<CourseRecord>      courseList = new ArrayList<>();     // For sorting
    private final PriorityQueue<Appeal>   appeals    = new PriorityQueue<>(); // Binary Heap demo

    // --- UI Components ---
    private DefaultTableModel tableModel;
    private JLabel            lblGpa;
    private JTextField        txtCode, txtName, txtGrade, txtUnits;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================
    public SmartGradeApp() {
        setTitle("SmartGrade - Final Project");
        setSize(750, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- Top Banner (Polymorphism Demo) ---
        Person student = new Student("Alex Rivera", "2026-0849");
        JLabel banner = new JLabel("  " + student.getRole(), JLabel.LEFT);
        banner.setForeground(Color.BLUE);
        add(banner, BorderLayout.NORTH);

        // --- Table ---
        String[] cols = {"Code", "Name", "Grade", "Units"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- Input Form ---
        JPanel form = new JPanel(new GridLayout(5, 2, 5, 5));
        txtCode  = new JTextField();
        txtName  = new JTextField();
        txtGrade = new JTextField();
        txtUnits = new JTextField();

        form.add(new JLabel("Code:"));  form.add(txtCode);
        form.add(new JLabel("Name:"));  form.add(txtName);
        form.add(new JLabel("Grade:")); form.add(txtGrade);
        form.add(new JLabel("Units:")); form.add(txtUnits);

        JButton btnAdd  = new JButton("Add Course");
        JButton btnSort = new JButton("Sort by Grade");

        btnAdd.addActionListener(e  -> addCourse());
        btnSort.addActionListener(e -> sortCoursesByGrade());

        form.add(btnAdd);
        form.add(btnSort);

        lblGpa = new JLabel("  GPA: 0.00 (Total Units: 0)", JLabel.LEFT);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(form,   BorderLayout.CENTER);
        southPanel.add(lblGpa, BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);

        // --- Load data from MySQL (replaces hardcoded sample data) ---
        loadDataFromDB();
    }

    // ============================================================
    // DATABASE HELPERS
    // ============================================================
    private Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(this,
                    "MySQL JDBC Driver not found!\n" +
                    "Add mysql-connector-j-x.x.x.jar to the classpath.");
            throw new SQLException("JDBC Driver missing", e);
        }
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    /** SELECT all rows from `courses` and populate the table + GPA label. */
    private void loadDataFromDB() {
        courseList.clear();
        String sql = "SELECT code, name, grade, units FROM courses ORDER BY code";
        try (Connection conn = getConnection();
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(sql)) {

            while (rs.next()) {
                courseList.add(new CourseRecord(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getDouble("grade"),
                        rs.getInt("units")));
            }
            refreshTable();
            calcGpa();

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading data from DB:\n" + ex.getMessage());
        }
    }

    /** INSERT a new record from the text fields. */
    private void addCourse() {
        String code  = txtCode.getText().trim().toUpperCase();
        String name  = txtName.getText().trim();
        String gText = txtGrade.getText().trim();
        String uText = txtUnits.getText().trim();

        // --- Validate inputs ---
        if (code.isEmpty() || name.isEmpty() || gText.isEmpty() || uText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields.");
            return;
        }

        double grade;
        int    units;
        try {
            grade = Double.parseDouble(gText);
            units = Integer.parseInt(uText);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Grade must be a decimal, Units must be an integer.");
            return;
        }

        if (grade < 1.0 || grade > 5.0) {
            JOptionPane.showMessageDialog(this, "Grade must be between 1.0 and 5.0.");
            return;
        }
        if (units <= 0) {
            JOptionPane.showMessageDialog(this, "Units must be greater than 0.");
            return;
        }

        // --- INSERT ---
        String sql = "INSERT INTO courses (code, name, grade, units) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, code);
            ps.setString(2, name);
            ps.setDouble(3, grade);
            ps.setInt   (4, units);
            ps.executeUpdate();

            // Clear fields + reload
            txtCode.setText(""); txtName.setText("");
            txtGrade.setText(""); txtUnits.setText("");
            loadDataFromDB();

        } catch (SQLIntegrityConstraintViolationException dup) {
            JOptionPane.showMessageDialog(this,
                    "Course code '" + code + "' already exists.");
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Database error:\n" + ex.getMessage());
        }
    }

    /** Sort the in-memory list (O(N log N)) and refresh table. */
    private void sortCoursesByGrade() {
        courseList.sort(Comparator.comparingDouble(CourseRecord::getGrade));
        refreshTable();
    }

    /** Re-render the JTable from courseList. */
    private void refreshTable() {
        tableModel.setRowCount(0);
        for (CourseRecord cr : courseList) {
            tableModel.addRow(new Object[]{
                    cr.getCode(), cr.getName(), cr.getGrade(), cr.getUnits()
            });
        }
    }

    /** Compute weighted GPA and update the label. */
    private void calcGpa() {
        if (courseList.isEmpty()) {
            lblGpa.setText("  GPA: 0.00 (Total Units: 0)");
            return;
        }
        double pts = 0;
        int    units = 0;
        for (CourseRecord cr : courseList) {
            pts   += cr.getGrade() * cr.getUnits();
            units += cr.getUnits();
        }
        double gpa = (units == 0) ? 0.0 : pts / units;
        lblGpa.setText(String.format("  GPA: %.2f (Total Units: %d)", gpa, units));
    }

    // ============================================================
    // MAIN
    // ============================================================
    public static void main(String[] args) {
        // Run GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> new SmartGradeApp().setVisible(true));
    }
}
