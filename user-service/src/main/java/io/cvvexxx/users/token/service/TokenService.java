package io.cvvexxx.users.token.service;

import io.cvvexxx.users.token.dto.TokenPairDto;
import io.cvvexxx.users.user.entity.User;

public interface TokenService {

    TokenPairDto issueTokens(User user);

    TokenPairDto refresh(String refreshToken);

    void revoke(String refreshToken);
}
