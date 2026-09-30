package com.medagent.memory;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 医学关键事实（跨话题常驻信息）。
 *
 * <p>为什么要单独一层：过敏史、慢病史、正在服用的药物这类信息，<b>无论用户后续切换到什么话题
 * 都必须参与推理</b>。如果只靠"相关性检索历史"，一旦话题切换这些信息会被判为不相关而丢弃，
 * 造成用药安全风险。因此这一层不参与相关性过滤，始终注入。</p>
 *
 * <p>更新策略是"安全信息只增不减"：过敏史 / 禁忌 / 慢病 / 在服药物做并集，
 * 即使摘要模型某一轮漏掉了旧信息，也不会从事实库里消失。</p>
 */
@Data
public class MedicalKeyFacts {

    private List<String> allergies = new ArrayList<>();
    private List<String> chronicConditions = new ArrayList<>();
    private List<String> currentMedications = new ArrayList<>();
    private List<String> contraindications = new ArrayList<>();

    private String age = "";
    private String gender = "";
    /** 妊娠 / 哺乳 / 备孕状态。 */
    private String pregnancy = "";

    private long updatedAt;

    public boolean isEmpty() {
        return isBlank(age) && isBlank(gender) && isBlank(pregnancy)
                && isEmpty(allergies) && isEmpty(chronicConditions)
                && isEmpty(currentMedications) && isEmpty(contraindications);
    }

    /** 渲染为可注入提示词的文本块；无内容时返回空串。 */
    public String toPromptText() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【患者长期背景 · 跨话题常驻，任何建议都不得与之冲突】\n");
        appendList(sb, "过敏史", allergies);
        appendList(sb, "慢性病史", chronicConditions);
        appendList(sb, "正在服用", currentMedications);
        appendList(sb, "已知禁忌", contraindications);
        String basic = joinBasic();
        if (!basic.isEmpty()) {
            sb.append("- 基本特征：").append(basic).append('\n');
        }
        if (!isBlank(pregnancy)) {
            sb.append("- 妊娠/哺乳：").append(pregnancy).append('\n');
        }
        return sb.toString();
    }

    private String joinBasic() {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(age)) {
            sb.append(age).append(" 岁");
        }
        if (!isBlank(gender)) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(gender);
        }
        return sb.toString();
    }

    private void appendList(StringBuilder sb, String label, List<String> values) {
        if (!isEmpty(values)) {
            sb.append("- ").append(label).append("：").append(String.join("、", values)).append('\n');
        }
    }

    /**
     * 安全优先合并：安全相关字段做并集（只增不减），基本特征以新抽取值为准。
     *
     * @param oldFacts  已有事实（可能为 null）
     * @param extracted 本轮从历史中新抽取的事实（可能为 null）
     */
    public static MedicalKeyFacts mergeSafetyFirst(MedicalKeyFacts oldFacts, MedicalKeyFacts extracted) {
        MedicalKeyFacts base = oldFacts == null ? new MedicalKeyFacts() : oldFacts;
        MedicalKeyFacts update = extracted == null ? new MedicalKeyFacts() : extracted;

        MedicalKeyFacts merged = new MedicalKeyFacts();
        merged.setAllergies(union(base.getAllergies(), update.getAllergies()));
        merged.setChronicConditions(union(base.getChronicConditions(), update.getChronicConditions()));
        merged.setCurrentMedications(union(base.getCurrentMedications(), update.getCurrentMedications()));
        merged.setContraindications(union(base.getContraindications(), update.getContraindications()));

        merged.setAge(isBlank(update.getAge()) ? base.getAge() : update.getAge());
        merged.setGender(isBlank(update.getGender()) ? base.getGender() : update.getGender());
        merged.setPregnancy(isBlank(update.getPregnancy()) ? base.getPregnancy() : update.getPregnancy());
        merged.setUpdatedAt(System.currentTimeMillis());
        return merged;
    }

    /** 保序去重并集，且剔除空串。 */
    private static List<String> union(List<String> a, List<String> b) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        addAll(set, a);
        addAll(set, b);
        return new ArrayList<>(set);
    }

    private static void addAll(LinkedHashSet<String> set, List<String> values) {
        if (values == null) {
            return;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                set.add(v.trim());
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isEmpty(List<String> list) {
        return list == null || list.isEmpty();
    }
}
