package hello.springdb1.service;

import org.springframework.transaction.annotation.Transactional;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MemberServiceV4 {

	private final MemberRepository repository;

	// 트랜잭션 AOP
	@Transactional
	public void accountTransfer(String fromId, String toId, int money) {
		bizLogic(fromId, toId, money);
	}

	private void bizLogic(String fromId, String toId, int money) {
		Member fromMember = repository.findById(fromId);
		Member toMember = repository.findById(toId);

		repository.update(fromId, fromMember.getMoney() - money);
		validation(toMember);
		repository.update(toId, toMember.getMoney() + money);

		log.info("계좌이체 완료: {} → {} 금액={}", fromId, toId, money);
	}

	private void validation(Member toMember) {
		if ("ex".equals(toMember.getMemberId())) {
			log.warn("이체 대상 검증 실패: memberId={}", toMember.getMemberId());
			throw new IllegalStateException("이체 중 예외 발생");
		}
	}

}
