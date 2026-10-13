import { useTranslation } from "react-i18next";

export function LaunchLoading() {
  const { t } = useTranslation();
  return <span className="crete-launch-loading" role="status" aria-label={t("spotify.loading", { defaultValue: "Loading..." })}>
    <span className="crete-launch-spinner" aria-hidden="true" />
  </span>;
}
