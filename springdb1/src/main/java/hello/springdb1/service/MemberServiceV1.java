package hello.springdb1.service;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepositoryV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MemberServiceV1 {

	private final MemberRepositoryV1 repository;
	
	public void accountTransfer(String fromId, String toId, int money) {
		Member fromMember = repository.findById(fromId);
		Member toMember = repository.findById(toId);
		
		repository.update(fromId, fromMember.getMoney() - money);
		validation(toMember);
		repository.update(toId, toMember.getMoney() + money);
	}
	
	private void validation(Member toMember) {
		if(toMember.getMemberId().equals("ex")) {
			throw new IllegalStateException("이체 중 예외 발생");
		}
	}
	
}
