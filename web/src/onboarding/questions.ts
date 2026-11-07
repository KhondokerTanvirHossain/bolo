/** Bolo's 3 setup questions (ONB-3), copy from the locked prototype; chips map to the API's stable keys. */
export type Field = "goal" | "fearedMoment" | "talkMinutes";

export type Question = {
  field: Field;
  en: string;
  bn: string;
  chips: { label: string; value: string }[];
};

export const QUESTIONS: readonly Question[] = [
  {
    field: "goal",
    en: "What do you need English for most?",
    bn: "ইংরেজি আপনার সবচেয়ে বেশি কোথায় দরকার?",
    chips: [
      { label: "Work meetings", value: "work_meetings" },
      { label: "Job interviews", value: "job_interviews" },
      { label: "Study abroad", value: "study_abroad" },
      { label: "Daily confidence", value: "daily_confidence" },
    ],
  },
  {
    field: "fearedMoment",
    en: "Which moment feels hardest?",
    bn: "কোন মুহূর্তটা সবচেয়ে কঠিন লাগে?",
    chips: [
      { label: "Speaking up in meetings", value: "speaking_up" },
      { label: "Interviews", value: "interviews" },
      { label: "Phone calls", value: "phone_calls" },
      { label: "Small talk", value: "small_talk" },
    ],
  },
  {
    field: "talkMinutes",
    en: "How long do you want to talk each day?",
    bn: "প্রতিদিন কতক্ষণ কথা বলতে চান?",
    chips: [
      { label: "5 minutes", value: "5" },
      { label: "10 minutes", value: "10" },
    ],
  },
];
