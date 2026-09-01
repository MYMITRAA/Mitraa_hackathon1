# Google and GitHub SSO setup

MiTRAA keeps email/password login and adds optional Google and GitHub sign-in. Social sign-in links only to an existing, enabled MiTRAA account with the same verified email. New participants must complete normal registration and email verification first.

## Local callback URLs

- Google: `http://localhost:8080/login/oauth2/code/google`
- GitHub: `http://localhost:8080/login/oauth2/code/github`

Use the HTTPS production hostname in each provider console for production, for example:

- `https://hackathons.mitratechgroup.com/login/oauth2/code/google`
- `https://hackathons.mitratechgroup.com/login/oauth2/code/github`

## Environment configuration

Copy `.env.example` to `.env`, then configure:

```text
SSO_ENABLED=true
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
```

Never commit `.env` or provider secrets. Google requires the `openid`, `profile`, and `email` scopes. GitHub requires `read:user` and `user:email` so private verified email addresses can be read.

Restart the application after changing these values. Flyway automatically creates the `oauth_identities` table with migration V11.
