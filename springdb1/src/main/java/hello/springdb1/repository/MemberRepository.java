package hello.springdb1.repository;

import hello.springdb1.domain.Member;

public interface MemberRepository {

	Member save(Member member);
	Member findById(String memberId);
	void update(String memberId, int money);
	void delete(String memberId);
	
	/**
	 * 테스트용
	 */
	void deleteAll();
	void initTable();
    void dropTable();
}
