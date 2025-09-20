package hello.springdb1.exception.translator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.support.JdbcUtils;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepositoryV4_1;
import hello.springdb1.repository.ex.MyDbException;
import hello.springdb1.repository.ex.MyDuplicateKeyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [데이터 접근 예외 직접 만들기]
 * 
 * - 특정 DB 오류는 '복구' 가능한 경우가 있음
 *    : 회원가입 시 ID 중복 -> 뒤에 숫자 붙여서 재가입 처리
 *  
 * - JDBC는 SQLException을 던지는데, 내부에 DB의 errorCode를 포함
 *   -> e.getErrorCode() 사용 가능
 *   H2 DB 기준:
 *      23505 : 키 중복 오류
 *      42000 : SQL 문법 오류
 *   (DB마다 코드 다름 → 매뉴얼 확인 필요)
 *   
 * - 서비스 계층에서 SQLException을 직접 사용하면?
 *   -> 서비스가 JDBC 기술에 의존하게 되어 순수성 깨짐
 *   -> 따라서 Repository 에서 SQLException -> 직접 정의한 예로 변환
 *     
 *     try {
 *     	// JDBC... 
 *      // SQLException is triggered. 
 *     } catch(SQLException e) {
 *     		throw new MyDbException(e); or throw new MyDuplicateKeyException(e);
 *     }
 *   
 * [예외 계층 구성]
 * 
 * MyDbException (런타임 예외, DB 관련 최상위 예외)
 *  └─ MyDuplicateKeyException (키 중복 전용 예외)
 * 
 * - 이렇게 하면:
 *   1) Repository 는 SQLException을 직접 던지지 않고,
 *      의미있는 예외(MyDuplicateKeyException, MyDbException)로 변환
 *   2) Service 는 JDBC 의존 없이, 순수한 사용자 정의 예외만 다룸
 *      (리포지토리 단에서 변환된 예외 SQLException -> My..Exception)
 *      
 * [핵심 정리]
 * - SQL ErrorCode를 통해 DB 오류 유형을 확인 가능
 * - Repository 계층에서 SQLException → "사용자 정의 예외로 변환"
 * - Service 계층은 "기술 종속성 없이", 의미 있는 예외만 다룸
 * - 복구 가능한 예외(MyDuplicateKeyException)는 잡아서 처리
 * - 복구 불가능한 예외(MyDbException)는 그대로 던짐
 * - 덕분에 서비스 계층은 순수성을 유지하면서도, 예외 상황에 유연하게 대처 가능      
 */

@Slf4j
@SpringBootTest
@RequiredArgsConstructor
public class ExTranslatorV1Test {

	/**
	 * 테스트용
	 */
	@Autowired
	private MemberRepositoryV4_1 repository;

	@Autowired
	private Service testService;
	
	@Test
	void duplicateKeySave() {
		testService.create("myId");
		testService.create("myId"); // 같은 아이디 저장 시도
	}

	@RequiredArgsConstructor
	static class Service {
		private final Repository repository;

		public void create(String memberId) {
			try {
				repository.save(new Member(memberId, 0));
				log.info("service.create : saveId = {}", memberId);
			} catch (MyDuplicateKeyException e) {
				log.info("MyDuplicateKeyException : 키 중복, 복구 시도");
				String retryId = generateNewId(memberId);
				log.info("MyDuplicateKeyException : retryId={}", retryId);
				repository.save(new Member(retryId, 0));
			} catch (MyDbException e) {
				log.info("MyDbException : 데이터 접근 계층 예외", e);
				throw e;
			}
		}

		private String generateNewId(String memberId) {
			return memberId + new Random().nextInt(10000);
		}
	}

	@RequiredArgsConstructor
	static class Repository {
		private final DataSource dataSource;

		public Member save(Member member) {
			String sql = "insert into member(member_id, money) values (?, ?)";

			Connection con = null;
			PreparedStatement pstmt = null;

			try {
				con = dataSource.getConnection();
				pstmt = con.prepareStatement(sql);
				pstmt.setString(1, member.getMemberId());
				pstmt.setInt(2, member.getMoney());

				int resultSize = pstmt.executeUpdate();
				log.info("save : {}, resultSize = {}", member, resultSize);
				return member;
			} catch (SQLException e) {
				if (e.getErrorCode() == 23505) {
					throw new MyDuplicateKeyException(e);
				} else {
					throw new MyDbException(e);
				}
			} finally {
				JdbcUtils.closeStatement(pstmt);
				JdbcUtils.closeConnection(con);
			}
		}
	}
	
	@BeforeEach
	void initialSetUp() {
		repository.dropTable();
		repository.initTable();
	}
	
	@AfterEach
	void afterEachTest() {
		repository.deleteAll();
		repository.dropTable();
	}
	
	@TestConfiguration
	@RequiredArgsConstructor
	static class TestConfig {

		// 의존성 자동 주입
		private final DataSource dataSource;
		
		@Bean
		MemberRepositoryV4_1 memberRepositoryV4_1() {
			return new MemberRepositoryV4_1(dataSource);
		}
		
		@Bean
		Repository testRepository() {
			return new Repository(dataSource);
		}
		
		@Bean
		Service testService() {
			return new Service(testRepository());
		}
		
	}
}
