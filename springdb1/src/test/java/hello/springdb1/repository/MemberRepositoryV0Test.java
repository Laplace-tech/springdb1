package hello.springdb1.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import hello.springdb1.domain.Member;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MemberRepositoryV0Test {

	private final MemberRepositoryV0 repository = new MemberRepositoryV0();

	@BeforeAll
	void initTable() throws SQLException {
		repository.dropTable();
		repository.initTable();
	}

	@AfterEach
	void AfterEach() throws SQLException {
		repository.deleteAll();
		repository.dropTable();
	}

	@Test
	void crud() throws SQLException {

		/**
		 * save : Unique index or primary key violation
		 */
		Member member = new Member("memberV0", 10000);
		repository.save(member);

		/**
		 * findById
		 */
		Member findMember = repository.findById(member.getMemberId());
		assertThat(findMember).isEqualTo(member);

		/**
		 * update: money : 10000 -> 9999
		 */
		repository.update(member.getMemberId(), 9999);
		Member updatedMember = repository.findById(member.getMemberId());
		assertThat(updatedMember.getMoney()).isEqualTo(9999);

		/**
		 * delete
		 */
		repository.delete(member.getMemberId());
		assertThatThrownBy(() -> repository.findById(member.getMemberId()))
			.isInstanceOf(NoSuchElementException.class);
	}

}
