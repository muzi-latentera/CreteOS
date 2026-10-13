import { useTranslation } from "react-i18next";
export const creteTextKey = (label: string) => label.replace(/…/g, "_ellipsis").replace(/[^a-zA-Z0-9]+/g, "_").replace(/_$/, "");
export function useCreteText() {
  const { t } = useTranslation();
  return (label: string, values?: Record<string, unknown>) => t(`crete.${creteTextKey(label)}`, { defaultValue: label, ...values });
}
