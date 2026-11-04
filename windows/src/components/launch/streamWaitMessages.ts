import messages from "./streamWaitMessages.json";
export const isStreamWaitMessage = (message: string) => messages.includes(message);
