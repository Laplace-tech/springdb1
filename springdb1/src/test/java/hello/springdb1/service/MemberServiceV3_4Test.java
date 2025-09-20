package hello.springdb1.service;

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

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepositoryV3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [트랜잭션 AOP와 @Repository, @Service 프록시 작동 원리]
 *  
 * 1. @Transactional 적용 원리
 * - @Transactional은 스프링 AOP 기반으로 동작
 * - 프록시는 @Transactional이 붙은 public 메서드를 감싸서 트랜잭션 시작, 커밋, 롤백 처리
 * - 즉, 트랜잭션 적용 대상은 @Transactional이 붙은 클래스/메서드
 * 
 * 2. @Service vs @Repository
 * - Service 계층 : 비즈니스 로직 + 트랜잭션 처리
 *  -> 서비스 메서드에 @Transactional이 붙어 있으면 AOP 프록시 생성
 * - Repository 계층 : 단순 CRUD만 제공, 보통 @Transactional 없음
 *  -> 원칙적으로 트랜잭션 프록시는 필요 없음
 *  
 * 3. @Repository에 프록시가 적용되는 경우
 * - @Repository 애너테이션 덕분에 스프링은 예외 변환 기능 제공
 *   (JDBC, JPA 예외 -> 스프링 DataAccessException 반환)
 * - 이 변환 과정이 내부적으로 프록시 기반으로 동작
 *  
 *
 * 4️. 요약
 *   계층         | @Transactional | AOP Proxy 적용 이유
 *   ------------|----------------|----------------------
 *   Service     | O              | 트랜잭션 처리
 *   Repository  | X              | 예외 변환(@Repository)
 */

/**
 * [스프링 부트 자동 리소스 등록 정리]
 * 
 * 1. 직접 등록 vs 자동 등록
 * - 직접 등록 : @Bean 으로 DataSource와 PlatformTransactionManager를 직접 생성
 *     -> 스프링 부트는 자동 등록하지 않음
 * - 자동 등록 : application.properties 설정 기반으로 스프링 부트가 자동 생성
 *     -> DataSource 이름 : dataSource
 *     -> 트랜잭션 매니저 이름 : transactionManager
 *     
 * 2. 데이터소스 자동 등록
 * - 설정: application.properties
 *      spring.datasource.url=jdbc:h2:file:C:/Users/CKIRUser/test
 *      spring.datasource.username=sa
 *      spring.datasource.password=
 * - 커넥션 풀: 기본 HikariCP
 * - spring.datasource.url 없으면 내장 메모리 DB 생성 시도
 *  
 * 3. 트랜잭션 매니저 자동 등록
 * - JDBC 사용 : DataSourceTransactionManager 자동 등록
 * - JPA 사용 : JpaTransactionManager 자동 등록
 * - 둘 다 사용 시 : JpaTransactionManager 우선 등록
 * - 직접 등록 시 자동 등록은 무시됨
 * 
 * 4. 테스트에서 자동 등록 활용
 * - @SpringBootTest 와 @TestConfiguration 사용 
 * - TestConfig 생성자에서 DataSource 주입 가능
 * - 별도 @Bean 등록 없이 MemberRepository, MemberService 생성
 * 
 */

@Slf4j
@SpringBootTest
public class MemberServiceV3_4Test {

    public static final String MEMBER_A = "memberA";
    public static final String MEMBER_B = "memberB";
    public static final String MEMBER_EX = "ex";

    @Autowired
    private MemberRepositoryV3 repository;

    @Autowired
    private MemberServiceV3_3 service;

    /**
     * [테스트용 설정]
     *  스프링 부트가 application.properties를 기반으로 
     *  "DataSource"와 "TransactionManager를 자동 등록
     *  TestConfig 에서는 'repository'와 'service'만 'Bean'으로 등록
     */
    @TestConfiguration
    @RequiredArgsConstructor
    static class TestConfig {

        private final DataSource dataSource; // 자동 생성된 DataSource 주입
        
        @Bean
        MemberRepositoryV3 memberRepositoryV3() {
            return new MemberRepositoryV3(dataSource);
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
        Assertions.assertThat(AopUtils.isAopProxy(service)).isTrue(); // 트랜잭션 AOP 프록시 확인
        Assertions.assertThat(AopUtils.isAopProxy(repository)).isFalse();
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
        assertThat(findMemberA.getMoney()).isEqualTo(10000); // 롤백 확인
        assertThat(findMemberEx.getMoney()).isEqualTo(10000);
    }
}
