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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepository;
import hello.springdb1.repository.MemberRepositoryV4_2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class MemberServiceV4Test {

	public static final String MEMBER_A = "memberA";
    public static final String MEMBER_B = "memberB";
    public static final String MEMBER_EX = "ex";

    @Autowired
    private MemberRepository repository;

    @Autowired
    private MemberServiceV4 service;

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

        private final DataSource dataSource; // 자동 생성된 DataSource 주입
        
        @Bean
        MemberRepository memberRepository() {
//          return new MemberRepositoryV4_1(dataSource); // 단순 예외 변환
        	return new MemberRepositoryV4_2(dataSource, sqlErrorCodeSQLExceptionTranslator());
//        	return new MemberRepositoryV5(jdbcTemplate());
        }
        
        /**
         * This is for MemberRepositoryV4_2
         */
        @Bean
        SQLErrorCodeSQLExceptionTranslator sqlErrorCodeSQLExceptionTranslator() {
        	return new SQLErrorCodeSQLExceptionTranslator(dataSource);
        }
     
        /**
         * This is for MemberRepositoryV5
         */
        @Bean
        JdbcTemplate jdbcTemplate() {
        	return new JdbcTemplate(dataSource);
        }
        
        @Bean
        MemberServiceV4 MemberService4() {
        	return new MemberServiceV4(memberRepository());
		}
    }
    
    @Test
    void AopCheck() {
        log.info("memberService class={}", service.getClass());
        log.info("memberRepository class={}", repository.getClass());
        Assertions.assertThat(AopUtils.isAopProxy(service)).isTrue(); // 트랜잭션 AOP 프록시 확인
        Assertions.assertThat(AopUtils.isAopProxy(repository)).isFalse(); // @Repository 프락시
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
