package hello.springdb1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 🔹 JDBC (Java Database Connectivity)
 *  - 자바에서 데이터베이스에 접속하고, SQL을 실행하고, 결과를 받을 수 있도록 해주는 API (표준 인터페이스)
 *  
 * 🔹 JDBC 등장 이유
 *  1. 각 DB 마다 커넥션 연결, SQL 실행, 결과 처리 방식이 제각각 -> 애플리케이션 코드가 DB 벤더마다 달라짐
 *  2. 개발자가 DB마다 다른 방법을 새로 학습해야 하는 문제 발생
 *  -> 자바에서 표준 인터페이스(JDBC)를 제공하여 모든 DB를 동일한 방식으로 접근 가능하게 함
 *   
 * 🔹 JDBC 표준 인터페이스
 *  - Connection : DB 연결
 *  - Statement : SQL 실행
 *  - ResultSet : SQL 실행 결과 조회
 *  
 * 🔹 JDBC 드라이버
 * 
 *  - DB 벤더(Oracle, MySQL, H2, ...) 가 자사 DB에 맞게 JDBC 인터페이스를 구현한 것
 *  - 예: MySQL JDBC 드라이버, Oracle JDBC 드라이버
 *  
 * 🔹 장점 
 *  - DB를 다른 종류로 변경해도 코드 수정 최소화 (드라이버만 교체)
 *  - 개발자는 JDBC 표준만 배우면 여러 DB 사용 가능
 * 
 * 🔹 한계
 *  - SQL 문법, 데이터 타입은 DB마다 조금씩 다름 (ANSI SQL 표준이 존재하지만 한계 있음)
 *  - 예: 페이징 쿼리는 DB별로 다 다르게 작성해야 함
 *  - 따라서 DB 변경 시 JDBC 코드는 그대로지만, SQL 문법은 바꿔야 할 수도 있음
 * 
 * 🔹 JDBC와 최신 기술
 *  - JDBC 자체는 오래되고 복잡 -> 더 편리한 추상화 기술이 등장
 *  - SQL Mapper : SQL은 직접 작성하되, JDBC 반복 작업을 줄여줌(ex: Spring JdbcTemplate, MyBatis)
 *  - ORM : SQL 작성 없이 객체 ↔ 테이블 매핑 (ex: JPA, Hibernate, EclispeLink)
 *  - SQL Mapper vs ORM -> 둘 다 내부적으로 JDBC를 사용
 *  
 * 🔹 정리
 *  - JDBC는 자바 개발자가 반드시 알아야 할 기본 기술
 *  - 직접 안 쓰더라도, 내부 원리를 알아야 MyBatis, JPA 같은 최신 기술을 더 잘 이해할 수 있음
 *  - 문제 발생 시 근본 원인 파악에도 필수
 *   
 */

@SpringBootApplication
public class Springdb1Application {

	public static void main(String[] args) {
		SpringApplication.run(Springdb1Application.class, args);
	}

}
