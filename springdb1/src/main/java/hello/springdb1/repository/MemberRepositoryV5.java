package hello.springdb1.repository;

import java.util.NoSuchElementException;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import hello.springdb1.domain.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ===========================
 * MemberRepositoryV5 (JdbcTemplate 기반)
 * ===========================
 * 1) JdbcTemplate 사용
 * - Connection, Statement, ResultSet "반복 제거"
 * - SQL 실행과정에서 "SQLException 발생 시 자동으로 DataAccessException 변환"
 * - "예외 변환기를 별도로 호출할 필요 없음"
 * 
 * 2) 스프링 예외 추상화 활용
 * - DataAccessException 최상위 예외를 기반으로 하위 예외 제공
 *   * DuplicateKeyException (복구 가능, 키 중복)
 *   * EmptyResultDataAccessException (조회 결과 없음)
 *   * 기타 DataAccessException (복구 불가)
 * - "서비스 계층은 기술 종속적 SQLException을 다루지 않고, 의미 있는 예외만 처리"
 * 
 * 3) 리포지토리 인터페이스 의존
 * - "MemberRepository 인터페이스를 통해 서비스 계층에 주입"
 * - 향후 구현체 변경(JDBC → JPA 등) 시 "서비스 계층 코드 변경 없음"
 * 
 * 4) 트랜잭션 처리
 * - "DataSourceUtils, 트랜잭션 AOP" 덕분에 커넥션 동기화 처리
 * - 서비스 계층에서 @Transactional 선언만으로 여러 SQL을 하나의 트랜잭션 단위로 묶어 처리 가능
 * 
 * 5) 코드 단순화
 * - 반복적인 JDBC try-catch-finally 제거
 * - 리포지토리 단에서는 SQL과 파라미터 지정만으로 CRUD 수행 가능
 */


/**
 * [V4_2 -> V5 개선 포인트]
 * 
 * - JdbcTemplate 도입
 * -> JDBC boilerplate 코드 (Connection, PreparedStatement, ResultSet, close) "제거"
 * -> SQLException을 스프링 DataAccessException으로 "자동 변환"
 * -> 예외 처리가 깔끔하고 일관됨
 * 
 * - RowMapper 사용
 * -> ResultSet → Member 객체 변환 로직 분리
 * -> queryForObject, query 등 편리한 메서드 활용 가능
 *   
 *   
 */
@Slf4j
@RequiredArgsConstructor
public class MemberRepositoryV5 implements MemberRepository {

	private final JdbcTemplate template;
	
	private final RowMapper<Member> memberRowMapper = (rs, rowNum) ->  {
		Member member = new Member();
		member.setMemberId(rs.getString("member_id"));
		member.setMoney(rs.getInt("money"));
		return member;
	};
	
    // ========== 테이블 관리 ==========
	@Override
	public void initTable() {
		String ddl = "create table if not exists member(" + 
					 "member_id varchar(10) primary key, " + 
					 "money integer not null default 0)";

		template.execute(ddl); // JdbcTemplate.execute는 SQLException 자동 변환
		log.info("Table member created!");
	}

	@Override
	public void dropTable() {
		String ddl = "drop table if exists member";
		template.execute(ddl);
		log.info("Table member dropped!");
	}

    // ========== CRUD ==========
	@Override
	public Member save(Member member) {
		String sql = "insert into member(member_id, money) values (?, ?)";
		template.update(sql, member.getMemberId(), member.getMoney());
		return member;
	}

	@Override
	public Member findById(String memberId) {
		String sql = "select * from member where member_id = ?";
		try {
			return template.queryForObject(sql, memberRowMapper, memberId);
		} catch (EmptyResultDataAccessException e) {
            // 조회 결과 없으면 NoSuchElementException으로 변환
            throw new NoSuchElementException("member not found memberId=" + memberId);
        }
	}

	@Override
	public void update(String memberId, int money) {
		String sql = "update member set money = ? where member_id = ?";
		int updated = template.update(sql, money, memberId);
        log.info("update result = {}", updated);
	}

	@Override
	public void delete(String memberId) {
        String sql = "delete from member where member_id = ?";
        int deleted = template.update(sql, memberId);
        log.info("delete result = {}", deleted);
    }

    @Override
    public void deleteAll() {
        String sql = "delete from member";
        int deleted = template.update(sql);
        log.info("deleteAll result = {}", deleted);
    }

}
