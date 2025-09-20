package hello.springdb1.repository;

import static hello.springdb1.Springdb1Application.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.NoSuchElementException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import hello.springdb1.domain.Member;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // @BeforeAll을 non-static 으로 사용 가능
public class MemberRepositoryV1Test {

	private final DataSource hikariDataSource = createHikariDataSource();
//	private final DataSource driverManagerdataSource = createDriverManagerDataSource();
	private final MemberRepositoryV1 repository = new MemberRepositoryV1(hikariDataSource);

	@BeforeAll
	void initTable() throws SQLException {
		repository.dropTable();
		repository.initTable();
	}

	@AfterEach
	void afterEach() {
		repository.deleteAll();
		repository.dropTable();
	}

	@Test
	void crud() throws SQLException {

		// given
		Member member = new Member("memberV1", 10000);

		// when
		repository.save(member);

		// then - find
		Member findMember = repository.findById(member.getMemberId());
		assertThat(findMember).isEqualTo(member);

		// then - update
		repository.update(member.getMemberId(), 8888);
		Member updatedMember = repository.findById(member.getMemberId());
		assertThat(updatedMember.getMoney()).isEqualTo(8888);

		// then - delete
		repository.delete(member.getMemberId());
		assertThatThrownBy(() -> repository.findById(member.getMemberId()))
		.isInstanceOf(NoSuchElementException.class);
	}

}
