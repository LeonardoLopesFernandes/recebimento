import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

/// Caminhão que atravessa a tela em loop: sai pela direita, reaparece
/// pela esquerda e volta ao centro; ao chegar no centro o texto
/// "recebimento" aparece.
class TruckLoader extends StatefulWidget {
  final String? status;
  const TruckLoader({super.key, this.status});

  @override
  State<TruckLoader> createState() => _TruckLoaderState();
}

const _kTruckSvg =
    '''<svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="0 0 24 24"><title>delivery-truck-speed</title><path fill="#ffffff" d="m.5 13.325l.5-2h5.5l-.5 2zm4.375 5.8Q4 18.25 4 17H1.5l.5-2.175h5.175l.9-3.65h2.1l1.25-5H4.5l.15-.6q.15-.7.688-1.137T6.6 4H18l-.925 4H20l3 4l-1 5h-2q0 1.25-.875 2.125T17 20t-2.125-.875T14 17h-4q0 1.25-.875 2.125T7 20t-2.125-.875M2.5 9.675l.5-2h6.5l-.5 2zM7 18q.425 0 .713-.288T8 17t-.288-.712T7 16t-.712.288T6 17t.288.713T7 18m10 0q.425 0 .713-.288T18 17t-.288-.712T17 16t-.712.288T16 17t.288.713T17 18m-1.075-5h4.825l.1-.525L19 10h-2.375z"/></svg>''';

class _TruckLoaderState extends State<TruckLoader>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c;

  @override
  void initState() {
    super.initState();
    _c = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2600),
    )..repeat();
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  double _dx(double t) {
    if (t < 0.35) return 0.8 * (t / 0.35);
    if (t < 0.45) return -0.8;
    if (t < 0.8) return -0.8 + 0.8 * ((t - 0.45) / 0.35);
    return 0.0;
  }

  double _textOpacity(double t) {
    if (t < 0.72) return 0.0;
    if (t > 0.84) return 1.0;
    return (t - 0.72) / 0.12;
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _c,
      builder: (_, __) {
        final t = _c.value;
        final w = MediaQuery.of(context).size.width;
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Transform.translate(
              offset: Offset(_dx(t) * w, 0),
              child: SvgPicture.string(
                _kTruckSvg,
                width: 84,
                height: 84,
              ),
            ),
            Opacity(
              opacity: _textOpacity(t),
              child: const Text(
                'recebimento',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.2,
                ),
              ),
            ),
            if (widget.status != null) ...[
              const SizedBox(height: 10),
              Text(
                widget.status!,
                style:
                    const TextStyle(color: Colors.white70, fontSize: 14),
              ),
            ],
          ],
        );
      },
    );
  }
}
