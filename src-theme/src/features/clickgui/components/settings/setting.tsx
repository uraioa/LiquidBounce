import { motion } from "framer-motion";

import styles from "./settings.module.scss";

type ModuleSettingItemProps = {
  children: React.ReactNode;
  className?: string;
};

export default function ModuleSetting(props: ModuleSettingItemProps) {
  return (
    <motion.div
      variants={{
        hidden: {
          opacity: 0,
          x: "-100%",
          transition: {
            bounce: 0,
            ease: "anticipate",
          },
        },
        visible: {
          opacity: 1,
          x: 0,
          transition: {
            bounce: 0,
            ease: "anticipate",
          },
        },
      }}
      className={styles.setting}
      {...props}
    />
  );
}