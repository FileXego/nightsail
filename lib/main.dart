import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'engine_client.dart';

/// M1 测试壳：故意做得"能用过关"而非好看。
/// 验收目标全部在这座页面上：发动锁机、看状态回流、验证引擎存活。
void main() {
  runApp(const ProviderScope(child: TestShell()));
}

class TestShell extends StatelessWidget {
  const TestShell({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '夜航 M1 测试壳',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(useMaterial3: true).copyWith(
        scaffoldBackgroundColor: const Color(0xFF0C0D12),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFE6CF9A), // 月升点缀色
          surface: Color(0xFF14151C),
        ),
      ),
      home: const TestPage(),
    );
  }
}

class TestPage extends ConsumerWidget {
  const TestPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final snap = ref.watch(engineSnapshotProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('夜航 M1 测试壳'), backgroundColor: Colors.transparent),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _StatusCard(snap: snap),
            const SizedBox(height: 16),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: [
                _armButton(ref, '睡眠锁 · 1分钟演示', 'sleep', 1),
                _armButton(ref, '禅定 · 1分钟', 'zen', 1),
                _armButton(ref, '番茄钟 · 25秒演示', 'pomodoro', 1),
              ],
            ),
            const SizedBox(height: 10),
            FilledButton.tonal(
              onPressed: () => ref.read(engineClientProvider).disarm(),
              child: const Text('手动解除（禅定应拒绝）'),
            ),
            const SizedBox(height: 24),
            const Text(
              '验收：① 锁屏页弹出 ② 桌面开抖音被拉回 ③ 锁屏页开微信能开 ④ 返回键无效 ⑤ 通知栏被禁用 ⑥ 息屏一小时再亮仍锁',
              style: TextStyle(fontSize: 12, color: Color(0xFF6E6858), height: 1.6),
            ),
          ],
        ),
      ),
    );
  }

  Widget _armButton(WidgetRef ref, String label, String mode, int minutes) {
    return FilledButton(
      style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14)),
      onPressed: () => ref.read(engineClientProvider).arm(mode, minutes),
      child: Text(label),
    );
  }
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.snap});
  final EngineSnapshot snap;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: const Color(0xFF14151C),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _row('引擎状态', snap.state),
            _row('模式', snap.mode ?? '—'),
            _row('剩余', '${snap.remainingSeconds}s'),
            _row('无障碍心跳', snap.accessibilityAlive ? '存活' : '丢失！去设置里重开'),
            _row('紧急解锁申请', snap.emergencyRequested ? '已发送' : '无'),
          ],
        ),
      ),
    );
  }

  Widget _row(String k, String v) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(k, style: const TextStyle(fontSize: 13, color: Color(0xFFB5AE9D))),
          Text(v, style: const TextStyle(fontSize: 13, fontFamily: 'monospace', color: Color(0xFFF2EEE4))),
        ],
      ),
    );
  }
}
