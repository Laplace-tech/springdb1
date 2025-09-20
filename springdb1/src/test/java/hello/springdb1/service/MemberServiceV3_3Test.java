package hello.springdb1.service;

import static hello.springdb1.Springdb1Application.createHikariDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepositoryV3;
import lombok.extern.slf4j.Slf4j;

/**
 * @SpringBootTest
 *  - 스프링 AOP를 적용하려면 반드시 스프링 컨테이너가 필요
 *  - 이 애너테이션이 있으면 테스트 시 스프링 부트를 통해 컨테이너 생성
 *  - @Autowired 등을 통해 컨테이너가 관리하는 스프링 빈을 주입받아 사용 가능
 *  
 *  
 * @TestConfiguration
 *  - 테스트 클래스 안에서만 사용할 별도의 설정 클래스를 정의할 때 사용
 *  - 이 애너테이션을 붙이면 스프링 부트가 자동으로 등록하는 빈들 외에 
 *    추가적으로 필요한 스프링 빈을 등록 가능
 *    
 * [TestConfig 내부 구성 예시]
 *   ▷ DataSource
 *       - 스프링에서 사용할 기본 DB 커넥션 풀(데이터소스)을 스프링 빈으로 등록
 *       - 트랜잭션 매니저 또한 이 DataSource 기반으로 동작
 *
 *   ▷ DataSourceTransactionManager
 *       - 트랜잭션 매니저를 스프링 빈으로 등록
 *       - 스프링 트랜잭션 AOP는 컨테이너에 등록된 트랜잭션 매니저를 자동 탐색해서 사용
 *       - 따라서 반드시 빈으로 등록해두어야 트랜잭션 AOP가 정상 동작
 * 
 */

@Slf4j
@SpringBootTest
public class MemberServiceV3_3Test {

	public static final String MEMBER_A = "memberA";
	public static final String MEMBER_B = "memberB";
	public static final String MEMBER_EX = "ex";
	
	@Autowired
	private MemberRepositoryV3 repository;
	
	@Autowired
	private MemberServiceV3_3 service;
	
	@TestConfiguration
	static class TestConfig {
		
		@Bean
		DataSource dataSource() {
			return createHikariDataSource();
		}
		
		@Bean
		PlatformTransactionManager transactionManager() {
			return new DataSourceTransactionManager(dataSource());
		}
		
		@Bean
		MemberRepositoryV3 memberRepositoryV3() {
			return new MemberRepositoryV3(dataSource());
		}      
		
		@Bean
        MemberServiceV3_3 memberServiceV3_3() {
            return new MemberServiceV3_3(memberRepositoryV3());
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
	
	@Test
	void AopCheck() {
	    log.info("memberService class={}", service.getClass());
	    log.info("memberRepository class={}", repository.getClass());
	    Assertions.assertThat(AopUtils.isAopProxy(service)).isTrue();
	    Assertions.assertThat(AopUtils.isAopProxy(repository)).isFalse(); // @Repository 프록시
	}
	
	@Test
	@DisplayName("정상 이체")
	void accountTransfer() throws SQLException {
		
		Member memberA = new Member(MEMBER_A, 10000);
		Member memberB = new Member(MEMBER_B, 10000);
		repository.save(memberA);
		repository.save(memberB);
		
		service.accountTransfer(MEMBER_A, MEMBER_B, 3000);
		
		Member findMemberA = repository.findById(MEMBER_A);
		Member findMemberB = repository.findById(MEMBER_B);
        assertThat(findMemberA.getMoney()).isEqualTo(7000);
        assertThat(findMemberB.getMoney()).isEqualTo(13000);
	}
	
	@Test
	@DisplayName("이체 중 예외 발생")
	void accountTransferEx() {
		
		Member memberA = new Member(MEMBER_A, 10000);
		Member memberEx = new Member(MEMBER_EX, 10000);
		repository.save(memberA);
		repository.save(memberEx);

		assertThatThrownBy(() -> service.accountTransfer(MEMBER_A, MEMBER_EX, 3000))
			.isInstanceOf(IllegalStateException.class);
		
		Member findMemberA = repository.findById(MEMBER_A);
		Member findMemberEx = repository.findById(MEMBER_EX);
        assertThat(findMemberA.getMoney()).isEqualTo(10000);
        assertThat(findMemberEx.getMoney()).isEqualTo(10000);
		
	}
	
}
