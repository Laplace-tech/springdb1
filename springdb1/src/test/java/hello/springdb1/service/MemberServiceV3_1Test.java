package hello.springdb1.service;

import static hello.springdb1.Springdb1Application.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepositoryV3;

public class MemberServiceV3_1Test {
	
	public static final String MEMBER_A = "memberA";
	public static final String MEMBER_B = "memberB";
	public static final String MEMBER_EX = "ex";
	
	private final DataSource dataSource = createHikariDataSource();
	private final PlatformTransactionManager transactionManager = createDataSourceTransactionManager(dataSource);
	private final MemberRepositoryV3 repository = new MemberRepositoryV3(dataSource);
	private final MemberServiceV3_1 service = new MemberServiceV3_1(transactionManager, repository);
	
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
