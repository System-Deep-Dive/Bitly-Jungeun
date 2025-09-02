import java.io.*;
import java.sql.*;
import java.util.*;
import java.net.*;
import javax.servlet.http.*;

/**
 * 여러 문제점이 있는 Java 클래스
 * 코드 리뷰 테스트용
 */
public class TestJavaClass {

    // 전역 변수 사용 (나쁜 예)
    private static String globalPassword = "123456";
    private static String apiKey = "sk-1234567890abcdef";
    private static List<String> globalData = new ArrayList<>();

    // 상수 대신 매직 넘버 사용
    private static final int MAGIC_NUMBER = 100;

    /**
     * 보안 취약점이 있는 메서드
     */
    public String vulnerableMethod(String userInput) {
        // SQL 인젝션 취약점
        String query = "SELECT * FROM users WHERE name = '" + userInput + "'";

        // 명령어 인젝션 취약점
        try {
            Runtime.getRuntime().exec("echo " + userInput);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // XSS 취약점 (웹 환경에서)
        String htmlOutput = "<div>" + userInput + "</div>";

        // 하드코딩된 자격 증명
        String dbPassword = "mypassword123";
        String dbUrl = "jdbc:mysql://localhost:3306/mydb";

        return query + htmlOutput;
    }

    /**
     * 예외 처리가 부족한 메서드
     */
    public void badExceptionHandling() {
        // 예외 처리 없음
        File file = new File("test.txt");
        FileInputStream fis = new FileInputStream(file);
        int data = fis.read();
        fis.close();

        // Null 체크 없음
        String[] array = new String[10];
        String firstElement = array[0];
        System.out.println(firstElement.length());

        // 리소스 누수 가능성
        Connection conn = null;
        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/test", "user", "pass");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM users");
            while (rs.next()) {
                System.out.println(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // conn.close() 호출 안함!
    }

    /**
     * 비효율적인 알고리즘
     */
    public List<String> inefficientAlgorithm(List<String> items) {
        List<String> result = new ArrayList<>();

        // O(n²) 복잡도의 중첩 반복문
        for (int i = 0; i < items.size(); i++) {
            for (int j = 0; j < items.size(); j++) {
                if (items.get(i).equals(items.get(j))) {
                    result.add(items.get(i));
                }
            }
        }

        // 불필요한 객체 생성
        String temp = new String("temporary");
        String anotherTemp = new String("another");

        // 중복 계산
        int sum1 = 0;
        for (String item : items) {
            sum1 += item.length();
        }

        int sum2 = 0;
        for (String item : items) {
            sum2 += item.length();
        }

        return result;
    }

    /**
     * 나쁜 클래스 설계
     */
    public static class BadInnerClass {
        private List<String> data;
        private int counter;

        public BadInnerClass() {
            this.data = new ArrayList<>();
            this.counter = 0;
        }

        // 타입 체크 없음
        public void addItem(Object item) {
            data.add(item.toString());
            counter++;
        }

        // 참조 노출 (캡슐화 위반)
        public List<String> getData() {
            return data; // 복사본 반환하지 않음
        }

        // 긴 메서드 (단일 책임 원칙 위반)
        public void processData() {
            List<String> processed = new ArrayList<>();

            for (String item : data) {
                if (item != null) {
                    if (item.length() > 10) {
                        processed.add(item.toUpperCase());
                    } else if (item.length() > 5) {
                        processed.add(item.toLowerCase());
                    } else {
                        processed.add(item);
                    }
                }
            }

            // 중복 로직
            List<String> result = new ArrayList<>();
            for (String item : processed) {
                result.add(item);
            }

            // 전역 변수 수정
            globalData.addAll(result);
        }
    }

    /**
     * 메모리 누수 가능성이 있는 메서드
     */
    public void potentialMemoryLeak() {
        List<String> largeList = new ArrayList<>();

        // 무한 루프 가능성
        for (int i = 0; i < 1000000; i++) {
            largeList.add("item" + i);

            // 메모리 해제 안함
            if (i % 1000 == 0) {
                System.out.println("Processed: " + i);
            }
        }

        // 스레드 안전성 문제
        globalData.addAll(largeList);
    }

    /**
     * 사용되지 않는 메서드
     */
    private void unusedMethod() {
        System.out.println("이 메서드는 사용되지 않습니다.");
    }

    /**
     * 메인 메서드
     */
    public static void main(String[] args) {
        TestJavaClass test = new TestJavaClass();

        // 문제가 있는 메서드들 호출
        test.vulnerableMethod("'; DROP TABLE users; --");
        test.badExceptionHandling();
        test.inefficientAlgorithm(Arrays.asList("a", "b", "c", "a", "b"));
        test.potentialMemoryLeak();

        // 내부 클래스 사용
        BadInnerClass badClass = new BadInnerClass();
        badClass.addItem("test");
        badClass.addItem(123);
        badClass.processData();

        System.out.println("테스트 완료");
    }
}
