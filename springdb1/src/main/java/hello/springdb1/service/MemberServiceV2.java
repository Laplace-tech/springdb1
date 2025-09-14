package hello.springdb1.service;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepositoryV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MemberServiceV2 {

    private final DataSource dataSource;
    private final MemberRepositoryV2 repository;

    public void accountTransfer(String fromId, String toId, int money) {
        Connection con = null;
        
        try {
            con = dataSource.getConnection(); // 커넥션 생성
            
            con.setAutoCommit(false); // 트랜잭션 시작
            log.info("트랜잭션 시작 - fromId={}, toId={}, money={}", fromId, toId, money);

            bizLogic(con, fromId, toId, money); // 비즈니스 로직 실행

            con.commit(); // 성공 시 커밋
            log.info("트랜잭션 커밋 성공");
        } catch (Exception e) {
            log.error("트랜잭션 중 예외 발생, 롤백 시도: {}", e.getMessage());
            rollback(con); // 실패 시 롤백
            throw new IllegalStateException(e);
        } finally {
            release(con); // 자원 해제
        }
    }

    private void bizLogic(Connection con, String fromId, String toId, int money) {
        Member fromMember = repository.findById(con, fromId);
        Member toMember = repository.findById(con, toId);

        repository.update(con, fromId, fromMember.getMoney() - money);
        validation(toMember);
        repository.update(con, toId, toMember.getMoney() + money);

        log.info("계좌이체 완료: {} → {} 금액={}", fromId, toId, money);
    }

    private void rollback(Connection con) {
        if (con != null) {
            try {
                con.rollback(); // 롤백
                log.info("트랜잭션 롤백 완료");
            } catch (SQLException e) {
                log.error("롤백 실패", e);
            }
        }
    }

    private void release(Connection con) {
        if (con != null) {
            try {
                con.setAutoCommit(true); // 커넥션 풀 고려
                con.close(); // 자원 반납
                log.info("커넥션 반환 완료");
            } catch (Exception e) {
                log.error("커넥션 반환 실패", e);
            }
        }
    }

    private void validation(Member toMember) {
        if ("ex".equals(toMember.getMemberId())) {
            log.warn("이체 대상 검증 실패: memberId={}", toMember.getMemberId());
            throw new IllegalStateException("이체 중 예외 발생");
        }
    }
}
